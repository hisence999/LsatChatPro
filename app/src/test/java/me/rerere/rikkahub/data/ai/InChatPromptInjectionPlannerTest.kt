package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.InjectionPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class InChatPromptInjectionPlannerTest {
    @Test
    fun atDepth_mapping_onThreeMessages() {
        val base = listOf(
            UIMessage.assistant("A"),
            UIMessage.assistant("B"),
            UIMessage.assistant("C"),
        )

        val depth0 = applyInChatPromptInjections(
            baseMessages = base,
            injections = listOf(
                InChatPromptInjection(
                    position = InjectionPosition.AT_DEPTH,
                    prompt = "D0",
                    depth = 0,
                )
            ),
        )
        assertEquals(listOf("A", "B", "C", "D0"), depth0.texts())

        val depth1 = applyInChatPromptInjections(
            baseMessages = base,
            injections = listOf(
                InChatPromptInjection(
                    position = InjectionPosition.AT_DEPTH,
                    prompt = "D1",
                    depth = 1,
                )
            ),
        )
        assertEquals(listOf("A", "B", "D1", "C"), depth1.texts())

        val depth2 = applyInChatPromptInjections(
            baseMessages = base,
            injections = listOf(
                InChatPromptInjection(
                    position = InjectionPosition.AT_DEPTH,
                    prompt = "D2",
                    depth = 2,
                )
            ),
        )
        assertEquals(listOf("A", "D2", "B", "C"), depth2.texts())

        val depth3 = applyInChatPromptInjections(
            baseMessages = base,
            injections = listOf(
                InChatPromptInjection(
                    position = InjectionPosition.AT_DEPTH,
                    prompt = "D3",
                    depth = 3,
                )
            ),
        )
        assertEquals(listOf("D3", "A", "B", "C"), depth3.texts())

        val depth4 = applyInChatPromptInjections(
            baseMessages = base,
            injections = listOf(
                InChatPromptInjection(
                    position = InjectionPosition.AT_DEPTH,
                    prompt = "D4",
                    depth = 4,
                )
            ),
        )
        assertEquals(listOf("D4", "A", "B", "C"), depth4.texts())
    }

    @Test
    fun beforeLatest_insertsBeforeLastUserOrAtEnd() {
        val withUser = listOf(
            UIMessage.assistant("A"),
            UIMessage.user("U1"),
            UIMessage.assistant("B"),
            UIMessage.user("U2"),
            UIMessage.assistant("C"),
        )

        val withUserResult = applyInChatPromptInjections(
            baseMessages = withUser,
            injections = listOf(
                InChatPromptInjection(
                    position = InjectionPosition.BEFORE_LATEST,
                    prompt = "INJ",
                )
            ),
        )
        assertEquals(listOf("A", "U1", "B", "INJ", "U2", "C"), withUserResult.texts())

        val withoutUser = listOf(
            UIMessage.assistant("A"),
            UIMessage.assistant("B"),
        )
        val withoutUserResult = applyInChatPromptInjections(
            baseMessages = withoutUser,
            injections = listOf(
                InChatPromptInjection(
                    position = InjectionPosition.BEFORE_LATEST,
                    prompt = "INJ",
                )
            ),
        )
        assertEquals(listOf("A", "B", "INJ"), withoutUserResult.texts())
    }

    @Test
    fun mixedPositions_keepStableOrderWithinSameSlot() {
        val base = listOf(
            UIMessage.user("U1"),
            UIMessage.assistant("A1"),
            UIMessage.user("U2"),
        )

        val result = applyInChatPromptInjections(
            baseMessages = base,
            injections = listOf(
                InChatPromptInjection(InjectionPosition.TOP_OF_CHAT, "M_TOP"),
                InChatPromptInjection(InjectionPosition.BEFORE_LATEST, "M_BEFORE"),
                InChatPromptInjection(InjectionPosition.AT_DEPTH, "M_DEPTH2", depth = 2),
                InChatPromptInjection(InjectionPosition.TOP_OF_CHAT, "E_TOP"),
                InChatPromptInjection(InjectionPosition.BEFORE_LATEST, "E_BEFORE"),
                InChatPromptInjection(InjectionPosition.AT_DEPTH, "E_DEPTH2", depth = 2),
            ),
        )

        assertEquals(
            listOf(
                "M_TOP",
                "E_TOP",
                "U1",
                "M_DEPTH2",
                "E_DEPTH2",
                "A1",
                "M_BEFORE",
                "E_BEFORE",
                "U2",
            ),
            result.texts(),
        )
    }

    @Test
    fun emptyChat_allInjectionsFallIntoSingleSlot() {
        val result = applyInChatPromptInjections(
            baseMessages = emptyList(),
            injections = listOf(
                InChatPromptInjection(InjectionPosition.TOP_OF_CHAT, "TOP"),
                InChatPromptInjection(InjectionPosition.BEFORE_LATEST, "BEFORE"),
                InChatPromptInjection(InjectionPosition.AT_DEPTH, "DEPTH", depth = 5),
            ),
        )

        assertEquals(listOf("TOP", "BEFORE", "DEPTH"), result.texts())
    }
}

private fun List<UIMessage>.texts(): List<String> {
    return mapNotNull { message ->
        message.parts.filterIsInstance<UIMessagePart.Text>().firstOrNull()?.text
    }
}
