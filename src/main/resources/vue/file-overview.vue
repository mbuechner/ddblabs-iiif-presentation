<template id="file-overview">
    <div class="nav flex-column nav-pills" id="v-pills-tab" role="tablist" aria-orientation="vertical">
            <template v-for="file in data">
                <a v-if="file.type === 'directory'" :href="`${Vue.prototype.$javalin.state.baseurl}/browse?d=${file.filenameWithPath}`" class="nav-link">{{file.filename}}/</a>
                <a v-else-if="file.type === 'file' && file.filenameWithPath === f" :href="`${Vue.prototype.$javalin.state.baseurl}/browse?d=${file.path}&f=${file.filenameWithPath}`" class="nav-link active">{{file.filename}}</a>
                <a v-else-if="file.type === 'file'" :href="`${Vue.prototype.$javalin.state.baseurl}/browse?d=${file.path}&f=${file.filenameWithPath}`" class="nav-link">{{file.filename}}</a>
            </template>
    </div>
</template>
<script src="https://cdnjs.cloudflare.com/ajax/libs/vue-router/3.1.3/vue-router.js" integrity="sha256-1Dr3ChysPawKq+arX2+sLoxyI/H6m4UVJWZOtvKdeQ8=" crossorigin="anonymous"></script>
<script>
    var router = new VueRouter({
        mode: 'history',
        routes: []
    });
    Vue.component("file-overview", {
        router,
        template: "#file-overview",
        data: () => ({
            data: [],
            f: "",
        }),
        created() {
            fetch(Vue.prototype.$javalin.state.baseurl + "/api/browse?d=" + (this.$route.query.d?this.$route.query.d:""))
                .then(res => res.json())
                .then(res => this.data = res)
                .catch(() => alert("Error while fetching files"));
                this.f = this.$route.query.f;
        },
    });
</script>