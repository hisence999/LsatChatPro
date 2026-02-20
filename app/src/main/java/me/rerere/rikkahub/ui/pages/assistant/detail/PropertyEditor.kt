package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.richtext.rememberHighlightCodeVisualTransformation
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState

private val jsonLenient = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

private fun toPrettyJson(element: JsonElement): String {
    return jsonLenient.encodeToString(JsonElement.serializer(), element)
}

private fun headersToJsonObject(headers: List<CustomHeader>): JsonObject {
    return JsonObject(
        buildMap {
            headers.forEach { header ->
                val key = header.name.trim()
                if (key.isNotBlank()) {
                    put(key, JsonPrimitive(header.value))
                }
            }
        }
    )
}

private fun customBodiesToJsonObject(customBodies: List<CustomBody>): JsonObject {
    return JsonObject(
        buildMap {
            customBodies.forEach { body ->
                val key = body.key.trim()
                if (key.isNotBlank()) {
                    put(key, body.value)
                }
            }
        }
    )
}

private fun headerValueToString(value: JsonElement): String {
    return when (value) {
        is JsonPrimitive -> value.content
        else -> toPrettyJson(value)
    }
}

@Composable
fun CustomHeaders(headers: List<CustomHeader>, onUpdate: (List<CustomHeader>) -> Unit) {
    val context = LocalContext.current
    val settings by rememberUserSettingsState()
    val useJsonEditor = settings.displaySetting.useJsonEditorForCustomRequest
    val headerJsonSnapshot = remember(headers) { toPrettyJson(headersToJsonObject(headers)) }
    var headersJsonText by remember(headerJsonSnapshot) { mutableStateOf(headerJsonSnapshot) }
    var jsonParseError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.assistant_page_custom_headers))
        Spacer(Modifier.height(8.dp))

        if (useJsonEditor) {
            OutlinedTextField(
                value = headersJsonText,
                onValueChange = { newJson ->
                    headersJsonText = newJson
                    try {
                        val parsed = if (newJson.isBlank()) {
                            JsonObject(emptyMap())
                        } else {
                            jsonLenient.parseToJsonElement(newJson)
                        }
                        val jsonObject = parsed as? JsonObject
                            ?: throw IllegalArgumentException(
                                context.getString(R.string.assistant_page_json_must_be_object)
                            )

                        onUpdate(
                            jsonObject.mapNotNull { (key, value) ->
                                val normalizedKey = key.trim()
                                if (normalizedKey.isBlank()) {
                                    null
                                } else {
                                    CustomHeader(
                                        name = normalizedKey,
                                        value = headerValueToString(value).trim()
                                    )
                                }
                            }
                        )
                        jsonParseError = null
                    } catch (e: Exception) {
                        jsonParseError = if (e is IllegalArgumentException) {
                            e.message
                        } else {
                            context.getString(
                                R.string.assistant_page_invalid_json,
                                e.message?.take(100) ?: ""
                            )
                        }
                    }
                },
                label = { Text(stringResource(R.string.assistant_page_json_editor_label)) },
                modifier = Modifier.fillMaxWidth(),
                isError = jsonParseError != null,
                supportingText = {
                    if (jsonParseError != null) {
                        Text(jsonParseError!!)
                    }
                },
                minLines = 8,
                maxLines = 35,
                visualTransformation = rememberHighlightCodeVisualTransformation(
                    language = "json",
                    code = headersJsonText
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        } else {
            headers.forEachIndexed { index, header ->
                var headerName by remember(header.name) { mutableStateOf(header.name) }
                var headerValue by remember(header.value) { mutableStateOf(header.value) }

                Card {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = headerName,
                                onValueChange = {
                                    headerName = it
                                    val updatedHeaders = headers.toMutableList()
                                    updatedHeaders[index] = updatedHeaders[index].copy(name = it.trim())
                                    onUpdate(updatedHeaders)
                                },
                                label = { Text(stringResource(R.string.assistant_page_header_name)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = headerValue,
                                onValueChange = {
                                    headerValue = it
                                    val updatedHeaders = headers.toMutableList()
                                    updatedHeaders[index] =
                                        updatedHeaders[index].copy(value = it.trim())
                                    onUpdate(updatedHeaders)
                                },
                                label = { Text(stringResource(R.string.assistant_page_header_value)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        IconButton(onClick = {
                            val updatedHeaders = headers.toMutableList()
                            updatedHeaders.removeAt(index)
                            onUpdate(updatedHeaders)
                        }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.assistant_page_delete_header)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val updatedHeaders = headers.toMutableList()
                    updatedHeaders.add(CustomHeader("", ""))
                    onUpdate(updatedHeaders)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.assistant_page_add_header))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.assistant_page_add_header))
            }
        }
    }
}

@Composable
fun CustomBodies(customBodies: List<CustomBody>, onUpdate: (List<CustomBody>) -> Unit) {
    val context = LocalContext.current
    val settings by rememberUserSettingsState()
    val useJsonEditor = settings.displaySetting.useJsonEditorForCustomRequest
    val bodyJsonSnapshot = remember(customBodies) { toPrettyJson(customBodiesToJsonObject(customBodies)) }
    var bodyJsonText by remember(bodyJsonSnapshot) { mutableStateOf(bodyJsonSnapshot) }
    var bodyJsonParseError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.assistant_page_custom_bodies))
        Spacer(Modifier.height(8.dp))

        if (useJsonEditor) {
            OutlinedTextField(
                value = bodyJsonText,
                onValueChange = { newJson ->
                    bodyJsonText = newJson
                    try {
                        val parsed = if (newJson.isBlank()) {
                            JsonObject(emptyMap())
                        } else {
                            jsonLenient.parseToJsonElement(newJson)
                        }
                        val jsonObject = parsed as? JsonObject
                            ?: throw IllegalArgumentException(
                                context.getString(R.string.assistant_page_json_must_be_object)
                            )

                        onUpdate(
                            jsonObject.mapNotNull { (key, value) ->
                                val normalizedKey = key.trim()
                                if (normalizedKey.isBlank()) {
                                    null
                                } else {
                                    CustomBody(
                                        key = normalizedKey,
                                        value = value
                                    )
                                }
                            }
                        )
                        bodyJsonParseError = null
                    } catch (e: Exception) {
                        bodyJsonParseError = if (e is IllegalArgumentException) {
                            e.message
                        } else {
                            context.getString(
                                R.string.assistant_page_invalid_json,
                                e.message?.take(100) ?: ""
                            )
                        }
                    }
                },
                label = { Text(stringResource(R.string.assistant_page_json_editor_label)) },
                modifier = Modifier.fillMaxWidth(),
                isError = bodyJsonParseError != null,
                supportingText = {
                    if (bodyJsonParseError != null) {
                        Text(bodyJsonParseError!!)
                    }
                },
                minLines = 8,
                maxLines = 35,
                visualTransformation = rememberHighlightCodeVisualTransformation(
                    language = "json",
                    code = bodyJsonText
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        } else {
            customBodies.forEachIndexed { index, body ->
                var bodyKey by remember(body.key) { mutableStateOf(body.key) }
                var bodyValueString by remember(body.value) {
                    mutableStateOf(toPrettyJson(body.value))
                }
                var jsonParseError by remember { mutableStateOf<String?>(null) }

                Card {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = bodyKey,
                                onValueChange = {
                                    bodyKey = it
                                    val updatedBodies = customBodies.toMutableList()
                                    updatedBodies[index] = updatedBodies[index].copy(key = it.trim())
                                    onUpdate(updatedBodies)
                                },
                                label = { Text(stringResource(R.string.assistant_page_body_key)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = bodyValueString,
                                onValueChange = { newString ->
                                    bodyValueString = newString
                                    try {
                                        val newJsonValue = jsonLenient.parseToJsonElement(newString)
                                        val updatedBodies = customBodies.toMutableList()
                                        updatedBodies[index] =
                                            updatedBodies[index].copy(value = newJsonValue)
                                        onUpdate(updatedBodies)
                                        jsonParseError = null
                                    } catch (e: Exception) {
                                        jsonParseError =
                                            context.getString(
                                                R.string.assistant_page_invalid_json,
                                                e.message?.take(100) ?: ""
                                            )
                                    }
                                },
                                label = { Text(stringResource(R.string.assistant_page_body_value)) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = jsonParseError != null,
                                supportingText = {
                                    if (jsonParseError != null) {
                                        Text(jsonParseError!!)
                                    }
                                },
                                minLines = 3,
                                maxLines = 5,
                                visualTransformation = rememberHighlightCodeVisualTransformation(
                                    language = "json",
                                    code = bodyValueString
                                ),
                                textStyle = LocalTextStyle.current.merge(fontFamily = FontFamily.Monospace),
                            )
                        }
                        IconButton(onClick = {
                            val updatedBodies = customBodies.toMutableList()
                            updatedBodies.removeAt(index)
                            onUpdate(updatedBodies)
                        }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.assistant_page_delete_body)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val updatedBodies = customBodies.toMutableList()
                    updatedBodies.add(CustomBody("", JsonPrimitive("")))
                    onUpdate(updatedBodies)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.assistant_page_add_body))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.assistant_page_add_body))
            }
        }
    }
}
