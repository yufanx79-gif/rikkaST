package org.xmlpull.v1

import java.io.InputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * C2 JVM 单测专用：org.xmlpull.v1.XmlPullParserFactory 的测试替身（Kotlin 版）。
 *
 * android.jar 里的 XmlPullParserFactory 是 Stub!，JVM 单测一调用就抛 RuntimeException: Stub!，
 * 导致 document 模块的 EpubParser / DocxParser 无法在纯 JVM 单测中运行。本类放在 app/src/test 下，
 * 利用测试源集先于 android.jar 出现在 classpath 的顺序替换 Stub 实现；用 JDK 自带 DOM + 动态代理
 * 实现最小可用的 XmlPullParser。生产代码不引用本类，只在 :app:testDebugUnitTest 生效。
 *
 * 注意：这里必须用 Kotlin 源文件。JDK 21 javac 会以「程序包已存在于另一个模块中: java.base」拒绝
 * Java 源文件对同包类的 shadow（JPMS 模块归属检查）。
 */
class XmlPullParserFactory private constructor() {

    var isNamespaceAware: Boolean = false

    fun newPullParser(): XmlPullParser {
        return Proxy.newProxyInstance(
            XmlPullParser::class.java.classLoader,
            arrayOf(XmlPullParser::class.java),
            DomPullParser(isNamespaceAware)
        ) as XmlPullParser
    }

    companion object {
        @JvmStatic
        fun newInstance(): XmlPullParserFactory = XmlPullParserFactory()
    }

    private class Event(
        val type: Int,
        val name: String?,
        val text: String?,
        val depth: Int,
        val attrs: Map<String, String>?,
    )

    private class DomPullParser(private val namespaceAware: Boolean) : InvocationHandler {
        private val events = ArrayList<Event>()
        private var index = -1

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            when (method.name) {
                "setInput" -> {
                    val first = args?.firstOrNull()
                    if (first is InputStream) parse(first)
                    return null
                }
                "setFeature", "setProperty", "defineEntityReplacementText", "close" -> return null
                "getFeature", "isAttributeDefault", "isEmptyElementTag", "isWhitespace" -> return false
                "next" -> {
                    if (index < events.size) index++
                    return currentType()
                }
                "getEventType" -> return currentType()
                "getName" -> return current()?.name
                "getText" -> return current()?.text
                "getDepth" -> return current()?.depth ?: 0
                "getAttributeValue" -> {
                    val attrName = args?.getOrNull(1) as? String ?: return null
                    return current()?.attrs?.get(attrName)
                }
                "getAttributeCount" -> return current()?.attrs?.size ?: 0
                "getInputEncoding" -> return "UTF-8"
                "getLineNumber", "getColumnNumber" -> return -1
                "getNamespaceCount" -> return 0
                else -> return defaultFor(method.returnType)
            }
        }

        private fun parse(input: InputStream) {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = namespaceAware
            val doc = factory.newDocumentBuilder().parse(input)
            events.add(Event(XmlPullParser.START_DOCUMENT, null, null, 0, null))
            appendElement(doc.documentElement, 1)
            events.add(Event(XmlPullParser.END_DOCUMENT, null, null, 0, null))
            index = 0
        }

        private fun appendElement(element: Element, depth: Int) {
            val attrs = HashMap<String, String>()
            val named = element.attributes
            for (i in 0 until named.length) {
                val a = named.item(i)
                a.nodeName?.let { attrs[it] = a.nodeValue }
                a.localName?.let { attrs[it] = a.nodeValue }
            }
            events.add(Event(XmlPullParser.START_TAG, tagName(element), null, depth, attrs))

            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                when (child.nodeType) {
                    Node.ELEMENT_NODE -> appendElement(child as Element, depth + 1)
                    Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                        val value = child.nodeValue
                        if (!value.isNullOrEmpty()) {
                            events.add(Event(XmlPullParser.TEXT, null, value, depth + 1, null))
                        }
                    }
                }
            }
            events.add(Event(XmlPullParser.END_TAG, tagName(element), null, depth, attrs))
        }

        private fun tagName(element: Element): String {
            val local = element.localName
            if (namespaceAware && local != null) return local
            return element.tagName
        }

        private fun current(): Event? = if (index in events.indices) events[index] else null

        private fun currentType(): Int {
            if (index < 0) return XmlPullParser.START_DOCUMENT
            return current()?.type ?: XmlPullParser.END_DOCUMENT
        }

        private fun defaultFor(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            else -> null
        }
    }
}