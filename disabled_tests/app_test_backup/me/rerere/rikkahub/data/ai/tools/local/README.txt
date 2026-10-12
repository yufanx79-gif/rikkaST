Moved out of app/src/test because rikkaST intentionally keeps its persistent
wang.harlon QuickJS engine instead of adopting upstream 2.5.6's dokar3 quickjs-kt.
The old test targets the upstream dokar3 evaluateJavascript() API, which is not present
in this product. Re-enable after either migrating the local JS engine or adding a
compatibility layer.
