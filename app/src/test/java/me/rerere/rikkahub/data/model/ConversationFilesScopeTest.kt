package me.rerere.rikkahub.data.model

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationFilesScopeTest {
    @Test
    fun `files only include file urls under upload`() {
        val uploadImage = "file:///data/user/0/me.rerere.rikkahub/files/upload/chat-image.png"
        val uploadDocument = "file:///data/user/0/me.rerere.rikkahub/files/upload/chat-doc.pdf"
        val nonUploadVideo = "file:///data/user/0/me.rerere.rikkahub/files/images/background.mp4"
        val externalAudio = "file:///storage/emulated/0/Music/demo.mp3"
        val remoteImage = "https://example.com/image.png"
        val uploadPrefixSpoof = "file:///data/user/0/me.rerere.rikkahub/files/upload_evil/fake.png"

        val conversation = Conversation(
            assistantId = Uuid.random(),
            messageNodes = listOf(
                MessageNode(
                    messages = listOf(
                        UIMessage(
                            role = MessageRole.USER,
                            parts = listOf(
                                UIMessagePart.Image(uploadImage),
                                UIMessagePart.Document(uploadDocument, fileName = "chat-doc.pdf"),
                                UIMessagePart.Video(nonUploadVideo),
                                UIMessagePart.Audio(externalAudio),
                                UIMessagePart.Image(remoteImage),
                                UIMessagePart.Image(uploadPrefixSpoof),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(uploadImage, uploadDocument),
            collectChatUploadFileUrls(conversation.messageNodes),
        )
    }
}
