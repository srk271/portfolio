package dev.bidu.core.model

/**
 * Risk class of a tool. The autonomy policy maps it to "just do it", "ask first" or "never".
 *
 * - [LOW]: read-only or trivially reversible local effects (time, battery, notes, reminders, torch).
 * - [MEDIUM]: hands an intent to another app where the user still has to act (dialer, cab app with
 *   destination pre-filled, ticket search). Nothing is spent or sent without the user.
 * - [HIGH]: sends a message, shares private data or destroys something (delete note/reminder).
 *   Always confirmed before execution.
 * - [CRITICAL]: payment, purchase, paid booking or money transfer completed by Bidu itself.
 *   Always confirmed, never batched, never retried. No shipped tool is CRITICAL today.
 */
enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
