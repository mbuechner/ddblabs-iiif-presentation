<template id="file-overview">
    <div aria-orientation="vertical" class="nav flex-column nav-pills" id="v-pills-tab" role="tablist">
        <a class="nav-link d-none" href="#" id="directoryup" title="Übergeordnetes Verzeichnis">..</a>
        <template v-for="file in data">
            <a :href="`${Vue.prototype.$javalin.state.baseurl}/browse?d=${file.filenameWithPath}`" class="nav-link"
               title="Verzeichnis" v-if="file.type === 'directory'">{{file.filename}}/</a>
            <a :href="`${Vue.prototype.$javalin.state.baseurl}/browse?d=${file.path}&f=${file.filenameWithPath}`"
               class="nav-link active" title="Ausgewählte Datei"
               v-else-if="file.type === 'file' && file.filenameWithPath === f">{{file.filename}}</a>
            <a :href="`${Vue.prototype.$javalin.state.baseurl}/browse?d=${file.path}&f=${file.filenameWithPath}`"
               class="nav-link" title="Datei" v-else-if="file.type === 'file'">{{file.filename}}</a>
        </template>
    </div>
</template>
<script>
    Vue.component("file-overview", {
        router: new VueRouter({
            mode: 'history',
            routes: []
        }),
        template: "#file-overview",
        data: () => ({
            data: [],
            f: "",
        }),
        created() {
            fetch(Vue.prototype.$javalin.state.baseurl + "/api/browse?d=" + (this.$route.query.d ? this.$route.query.d : ""))
                .then(res => res.json())
                .then(res => this.data = res)
                .catch(() => alert("Error while fetching files"));
            this.f = this.$route.query.f;
        },
    });
</script>