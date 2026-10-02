package com.harambee.tracker.core

/** How contributor names appear in anything shared outside the app. */
object NameDisplay {
    const val FULL = "FULL"
    const val FIRST_INITIAL = "FIRST_INITIAL"
    const val INITIALS = "INITIALS"
    const val HIDDEN = "HIDDEN"

    val all = listOf(FULL to "Full name", FIRST_INITIAL to "First name + initial", INITIALS to "Initials only", HIDDEN to "No names")
    const val ANONYMOUS = "Well-wisher"

    /** "Jane Wanjiku" -> "Jane W." / "J.W." / "Well-wisher"; titles like "CO" are kept. */
    fun apply(name: String, mode: String, anonymous: Boolean = false): String {
        if (anonymous || mode == HIDDEN) return ANONYMOUS
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || mode == FULL) return name.trim()
        val titles = words.takeWhile { Names.isTitle(it) }
        val rest = words.drop(titles.size).ifEmpty { return name.trim() }
        val shown = when (mode) {
            FIRST_INITIAL -> if (rest.size == 1) rest[0] else rest.first() + " " + rest.last().first().uppercaseChar() + "."
            INITIALS -> rest.joinToString("") { it.first().uppercaseChar() + "." }
            else -> rest.joinToString(" ")
        }
        return (titles + shown).joinToString(" ")
    }
}

object Milestones {
    val steps = listOf(25, 50, 75, 100)

    /** The highest milestone passed when the total went from [before] to [after]. */
    fun crossed(before: Long, after: Long, target: Long?): Int? {
        if (target == null || target <= 0) return null
        return steps.filter { m -> before * 100 < m * target && after * 100 >= m * target }.maxOrNull()
    }

    /** The highest milestone already reached. */
    fun reached(total: Long, target: Long?): Int? =
        if (target == null || target <= 0) null else steps.filter { total * 100 >= it * target }.maxOrNull()

    fun headline(milestone: Int) = when (milestone) {
        25 -> "A quarter of the way! 🙌"
        50 -> "Halfway there! 🎉"
        75 -> "Three quarters done! 💪"
        else -> "Target reached! 🎉🙏"
    }
}

/** Shared figures for the short update formats. */
data class Tally(val totalCents: Long, val contributors: Int, val targetCents: Long?) {
    fun lines(): String = buildString {
        append("*Total received: KES ").append(Money.format(totalCents)).append("*")
        append("\nContributors: ").append(contributors)
        if (targetCents != null && targetCents > 0) {
            append("\nTarget: KES ").append(Money.format(targetCents)).append(" (").append(totalCents * 100 / targetCents).append("%)")
            val balance = targetCents - totalCents
            append(if (balance > 0) "\nBalance: KES ${Money.format(balance)}" else "\nTarget reached 🎉")
        }
    }
}

/** The short formats: one contribution, the batch since the last update, and milestones. */
object UpdateFormats {
    private fun payToLine(payTo: List<PayTo>) =
        payTo.filter { it.label().isNotBlank() }.takeIf { it.isNotEmpty() }
            ?.joinToString(" or ", prefix = "\nSend your contribution to ") { "*${it.label()}*" } ?: ""

    /** Announces one contribution: "✅ Received with thanks: Jane W. — KES 1,000". */
    fun single(campaign: String, name: String, amountCents: Long, showAmount: Boolean, tally: Tally, payTo: List<PayTo>): String = buildString {
        append("✅ *Received with thanks*\n")
        append(name)
        if (showAmount) append(" — KES ").append(Money.format(amountCents))
        append("\nAsante sana 🙏\n\n")
        append("*").append(campaign).append("*\n")
        append(tally.lines())
        append(payToLine(payTo))
    }.trimEnd()

    /**
     * Only the people counted since the last update, keeping their numbers from the full list,
     * so the group can follow on from the previous post.
     */
    fun batch(campaign: String, lines: List<UpdateLine>, options: UpdateOptions, tally: Tally, payTo: List<PayTo>): String = buildString {
        val ordered = WhatsAppUpdateBuilder.arrange(lines.filter { it.paid || options.showPledges }, options.combineRepeat, options.sortByAmount)
        val fresh = ordered.withIndex().filter { it.value.isNew && it.value.paid }
        append("*").append(campaign).append(" — new contributions*\n")
        if (fresh.isEmpty()) {
            append("_No new contributions since the last update._\n")
        } else {
            val freshTotal = fresh.sumOf { it.value.amountCents }
            append("_Since the last update: ").append(fresh.size).append(if (fresh.size == 1) " person" else " people")
            append(", KES ").append(Money.format(freshTotal)).append("_\n")
            fresh.forEach { (i, l) ->
                append(i + 1).append(". ").append(l.name)
                if (options.showAmounts) append(" ").append(Money.format(l.amountCents))
                append(" ✅\n")
            }
        }
        append("\n").append(tally.lines())
        append(payToLine(payTo))
        append("\nThank you 🙏")
    }.trimEnd()

    fun milestone(campaign: String, milestone: Int?, tally: Tally, payTo: List<PayTo>): String = buildString {
        val target = tally.targetCents
        if (milestone != null && target != null) {
            append("*").append(Milestones.headline(milestone)).append("*\n")
            append("*").append(campaign).append("* has reached *").append(milestone).append("%* of the target.\n")
        } else {
            append("*").append(campaign).append(" — progress update*\n")
        }
        append("KES ").append(Money.format(tally.totalCents))
        if (target != null && target > 0) append(" of KES ").append(Money.format(target))
        append(" raised by ").append(tally.contributors).append(if (tally.contributors == 1) " contributor.\n" else " contributors.\n")
        if (target != null && target > tally.totalCents) {
            append("Balance: *KES ").append(Money.format(target - tally.totalCents)).append("*\n")
            append("Thank you all. Let's finish strong 💪")
        } else {
            append("Thank you all for your generosity 🙏")
        }
        append(payToLine(payTo))
    }.trimEnd()
}
