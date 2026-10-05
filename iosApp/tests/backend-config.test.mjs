import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import test from 'node:test';

const project = fileURLToPath(new URL('../iosApp.xcodeproj', import.meta.url));
const plist = fileURLToPath(new URL('../iosApp/Info.plist', import.meta.url));

// Uses Xcode's actual SDK-conditional resolution, not a hand-written xcconfig parser.
// Read-only: no compile, simulator boot/install, signing, prefs or backend requests.
function settings(sdk, overrides = []) {
  const result = JSON.parse(execFileSync('xcodebuild', [
    '-project', project, '-scheme', 'iosApp', '-configuration', 'Debug',
    '-sdk', sdk, '-destination', sdk === 'iphoneos' ? 'generic/platform=iOS' : 'generic/platform=iOS Simulator',
    '-showBuildSettings', '-json', ...overrides,
  ], { encoding: 'utf8', timeout: 60000, maxBuffer: 4 * 1024 * 1024 }));
  return result.find(item => item.target === 'iosApp').buildSettings;
}

test('simulator API uses Mac loopback while physical device uses development LAN', () => {
  const simulator = settings('iphonesimulator');
  const device = settings('iphoneos');
  assert.equal(simulator.BOARDERLESS_BACKEND_HOST, '127.0.0.1');
  assert.equal(device.BOARDERLESS_BACKEND_HOST, '192.168.68.67');
  for (const resolved of [simulator, device]) {
    assert.equal(resolved.BOARDERLESS_BACKEND_SCHEME, 'http');
    assert.equal(resolved.BOARDERLESS_BACKEND_PORT, '3000');
  }
  const source = JSON.parse(execFileSync('plutil', ['-convert', 'json', '-o', '-', plist], { encoding: 'utf8' }));
  assert.equal(source.BoarderLessBackendURL,
    '$(BOARDERLESS_BACKEND_SCHEME)://$(BOARDERLESS_BACKEND_HOST):$(BOARDERLESS_BACKEND_PORT)');
  assert.equal(source.NSAppTransportSecurity.NSAllowsLocalNetworking, true);
  assert.ok(source.NSLocalNetworkUsageDescription);
  assert.notEqual(source.NSAppTransportSecurity.NSAllowsArbitraryLoads, true);
});

test('explicit deployment overrides take precedence over device development defaults', () => {
  const device = settings('iphoneos', [
    'BOARDERLESS_BACKEND_HOST=api.example.invalid', 'BOARDERLESS_BACKEND_SCHEME=https',
    'BOARDERLESS_BACKEND_PORT=443',
  ]);
  assert.equal(device.BOARDERLESS_BACKEND_HOST, 'api.example.invalid');
  assert.equal(device.BOARDERLESS_BACKEND_SCHEME, 'https');
  assert.equal(device.BOARDERLESS_BACKEND_PORT, '443');
});
