<template id="file-overview">
    <div>
        <ul class="file-overview-list">
            <li v-for="file in data">
                <a v-if="file.type === 'directory'" :href="`browse?d=${file.filenameWithPath}`">{{file.filename}}/</a>
                <a v-else-if="file.type === 'file' && file.filenameWithPath === f" :href="`browse?d=${file.path}&f=${file.filenameWithPath}`" class="font-weight-bold">{{file.filename}}</a>                
                <a v-else-if="file.type === 'file'" :href="`browse?d=${file.path}&f=${file.filenameWithPath}`">{{file.filename}}</a>
            </li>
        </ul>
    </div>
</template>
<script src="https://cdn.jsdelivr.net/npm/vue-router"></script>
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
            fetch("/api/browse?d=" + (this.$route.query.d?this.$route.query.d:""))
                .then(res => res.json())
                .then(res => this.data = res)
                .catch(() => alert("Error while fetching files"));
                this.f = this.$route.query.f;
        },
    });
</script>