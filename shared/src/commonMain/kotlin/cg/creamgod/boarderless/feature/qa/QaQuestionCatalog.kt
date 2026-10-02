package cg.creamgod.boarderless.feature.qa

import cg.creamgod.boarderless.i18n.Strings

/** Stable IDs preserve draft answers as language changes; no build result pre-fills acceptance. */
fun qaQuestionCatalog(): List<QaQuestion> = listOf(
    QaQuestion("functional", Strings.qa.categoryFunctional(), Strings.qa.questionFunctional()),
    QaQuestion("persistence", Strings.qa.categoryFunctional(), Strings.qa.questionPersistence()),
    QaQuestion("permissions", Strings.qa.categoryFunctional(), Strings.qa.questionPermissions()),
    QaQuestion("mobile", Strings.qa.categoryMobile(), Strings.qa.questionMobileInteraction()),
    QaQuestion("visual", Strings.qa.categoryProduct(), Strings.qa.questionVisual(), requiresProductSignoff = true),
    QaQuestion("occlusion", Strings.qa.categoryProduct(), Strings.qa.questionOcclusion(), requiresProductSignoff = true),
    QaQuestion("operation", Strings.qa.categoryProduct(), Strings.qa.questionOperationOwnership(), requiresProductSignoff = true),
    QaQuestion("voiceover", Strings.qa.categoryAccessibility(), Strings.qa.questionVoiceOver()),
    QaQuestion("media-assets", Strings.qa.categoryMedia(), Strings.qa.questionMediaAssets(), sopSection = "5.1–5.8", evidenceHint = Strings.qa.evidenceMediaAssets()),
    QaQuestion("media-gif", Strings.qa.categoryMedia(), Strings.qa.questionMediaGif(), sopSection = "5.10–5.13", evidenceHint = Strings.qa.evidenceMediaGif()),
    QaQuestion("media-video", Strings.qa.categoryMedia(), Strings.qa.questionMediaVideo(), sopSection = "5.14–5.17", evidenceHint = Strings.qa.evidenceMediaVideo()),
    QaQuestion("media-display", Strings.qa.categoryMedia(), Strings.qa.questionMediaDisplay(), sopSection = "5.17", evidenceHint = Strings.qa.evidenceMediaDisplay()),
    QaQuestion("media-lifecycle", Strings.qa.categoryMedia(), Strings.qa.questionMediaLifecycle(), sopSection = "5.14–5.17", evidenceHint = Strings.qa.evidenceMediaLifecycle()),
    QaQuestion("media-errors", Strings.qa.categoryMedia(), Strings.qa.questionMediaErrors(), sopSection = "5.18", evidenceHint = Strings.qa.evidenceMediaErrors()),
    QaQuestion("media-package", Strings.qa.categoryMedia(), Strings.qa.questionMediaPackage(), sopSection = "5.19", evidenceHint = Strings.qa.evidenceMediaPackage()),
    QaQuestion("media-visual", Strings.qa.categoryProduct(), Strings.qa.questionMediaVisual(), requiresProductSignoff = true,
        sopSection = "5.14–5.17", evidenceHint = Strings.qa.evidenceMediaVisual()),
    QaQuestion("regression", Strings.qa.categoryRegression(), Strings.qa.questionRegression()),
    QaQuestion("performance", Strings.qa.categoryDeferred(), Strings.qa.questionPerformanceDeferred()),
)
