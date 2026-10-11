package com.haodong.yimalaile.domain.export

/**
 * English strings used inside the exported PDF. Kept separate from
 * Compose string resources because the PDF body language is independent
 * of the current UI locale (English only).
 */
internal data class ExportStrings(
    val reportTitle: String,
    val generatedOn: String,
    val summaryHeader: String,
    val summaryTotalRecords: String,
    val summaryAverageCycle: String,
    val summaryAveragePeriod: String,
    val recordsHeader: String,
    val recordIndex: String,
    val recordStart: String,
    val recordEnd: String,
    val recordDuration: String,
    val recordOngoing: String,
    val recordNoEnd: String,
    val dailyHeader: String,
    val dailyIntensity: String,
    val dailyMood: String,
    val dailySymptoms: String,
    val dailyNotes: String,
    val intensityLight: String,
    val intensityMedium: String,
    val intensityHeavy: String,
    val moodHappy: String,
    val moodNeutral: String,
    val moodSad: String,
    val moodVerySad: String,
    val unitDays: String,
    val noRecords: String,
    val disclaimer: String,
    val pageFooter: String,
) {
    companion object {
        fun forLanguage(@Suppress("UNUSED_PARAMETER") language: String): ExportStrings = English

        val English = ExportStrings(
            reportTitle = "Menstrual Cycle Report",
            generatedOn = "Generated on",
            summaryHeader = "Summary",
            summaryTotalRecords = "Total records",
            summaryAverageCycle = "Average cycle length",
            summaryAveragePeriod = "Average period length",
            recordsHeader = "Records",
            recordIndex = "Record",
            recordStart = "Start",
            recordEnd = "End",
            recordDuration = "Duration",
            recordOngoing = "ongoing",
            recordNoEnd = "—",
            dailyHeader = "Daily log",
            dailyIntensity = "Flow",
            dailyMood = "Mood",
            dailySymptoms = "Symptoms",
            dailyNotes = "Notes",
            intensityLight = "Light",
            intensityMedium = "Medium",
            intensityHeavy = "Heavy",
            moodHappy = "Happy",
            moodNeutral = "Neutral",
            moodSad = "Low",
            moodVerySad = "Bad",
            unitDays = "days",
            noRecords = "No records to export.",
            disclaimer = "This report is for personal reference only and is not medical advice.",
            pageFooter = "Page",
        )
    }
}
