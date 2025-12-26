package me.rerere.rikkahub.data.ai.prompts

internal val DEFAULT_WELCOME_PHRASES_PROMPT = """
    You are generating short welcome greetings that the assistant will show on a brand-new (empty) chat screen.

    Generation rules:
    1. Language: {locale}
    2. Count: exactly 10 greetings.
    3. Length: Chinese 6–10 characters each; other languages similar short length.
    4. Style: natural, friendly, and suitable as a greeting.
    5. The greetings must be consistent with the assistant's persona and system prompt.

    Output format (STRICT):
    1. Output ONLY the greetings themselves.
    2. Use the ASCII character # as the ONLY separator.
    3. Do NOT output newlines.
    4. Do NOT add any extra text before or after.
""".trimIndent()

