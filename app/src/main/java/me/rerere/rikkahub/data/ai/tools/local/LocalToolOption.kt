package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("present_file")
    data object PresentFile : LocalToolOption()

    @Serializable
    @SerialName("python_engine")
    data object PythonEngine : LocalToolOption()

    @Serializable
    @SerialName("file_tools")
    data object FileTools : LocalToolOption()

    @Serializable
    @SerialName("shell_tools")
    data object ShellTools : LocalToolOption()


    @Serializable
    @SerialName("database_query")
    data object DatabaseQuery : LocalToolOption()

    @Serializable
    @SerialName("task_tools")
    data object TaskTools : LocalToolOption()

    @Serializable
    @SerialName("calculator")
    data object Calculator : LocalToolOption()

    @Serializable
    @SerialName("worker_tools")
    data object WorkerTools : LocalToolOption()

    @Serializable
    @SerialName("teammate_tools")
    data object TeammateTools : LocalToolOption()

    @Serializable
    @SerialName("send_message")
    data object SendMessage : LocalToolOption()

    @Serializable
    @SerialName("screen_time")
    data object ScreenTime : LocalToolOption()

    @Serializable
    @SerialName("calendar")
    data object Calendar : LocalToolOption()

    @Serializable
    @SerialName("chart_display")
    data object ChartDisplay : LocalToolOption()
}

