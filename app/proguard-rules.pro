# rikkaST R8 keep rules
#
# snakeyaml（角色卡 / 技能的 YAML front matter 解析）内部通过 java.beans.* 反射
# 访问 JavaBean 属性，Android 运行库没有这套类，只影响 R8 的静态分析报错。
-dontwarn java.beans.**
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**
