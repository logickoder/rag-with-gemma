package dev.logickoder.ragwithgemma.data.ingestion

import dev.logickoder.ragwithgemma.data.model.Population

data class SectionInfo(val population: Population, val label: String)

object SectionMap {
    private val map = mapOf(
        0 to SectionInfo(Population.ADULT, "Dosing"),
        1 to SectionInfo(Population.PEDIATRIC, "Dosing"),
        13 to SectionInfo(Population.GERIATRIC, "Dosing"),
        3 to SectionInfo(Population.GENERAL, "Drug Interactions"),
        4 to SectionInfo(Population.GENERAL, "Adverse Effects"),
        5 to SectionInfo(Population.GENERAL, "Warnings & Contraindications"),
        6 to SectionInfo(Population.GENERAL, "Pregnancy & Lactation"),
        10 to SectionInfo(Population.GENERAL, "Pharmacology"),
        11 to SectionInfo(Population.GENERAL, "Administration"),
    )

    fun lookup(sectionNumber: Int): SectionInfo =
        map[sectionNumber] ?: SectionInfo(Population.GENERAL, "Section $sectionNumber")
}
