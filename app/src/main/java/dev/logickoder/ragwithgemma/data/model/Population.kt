package dev.logickoder.ragwithgemma.data.model

enum class Population(val tableSuffix: String, val label: String) {
    ADULT("adult", "Adult"),
    PEDIATRIC("pediatric", "Pediatric"),
    GERIATRIC("geriatric", "Geriatric"),
    GENERAL("general", "General");

    companion object {
        fun fromLabel(label: String): Population =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: GENERAL
    }
}
