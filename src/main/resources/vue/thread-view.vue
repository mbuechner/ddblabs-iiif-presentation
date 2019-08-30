<template id="thread-view">
    <div>{{ $javalin.pathParams["user"] }}</div>
</template>
<script>
    Vue.component("thread-view", {
        template: "#thread-view"
    });
</script>