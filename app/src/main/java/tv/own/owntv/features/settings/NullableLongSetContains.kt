package tv.own.owntv.features.settings

/** Allows concise membership checks when a source id is optional. */
internal operator fun Set<Long>.contains(value: Long?): Boolean = value != null && this.contains(value)
