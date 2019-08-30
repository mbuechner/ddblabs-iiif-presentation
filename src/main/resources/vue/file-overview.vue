<template id="file-overview">
    <div>
        <ul class="file-overview-list">
            <li v-for="file in data">
                <a :href="`/${file.name}`">{{file.name}}</a>
            </li>
        </ul>
    </div>
</template>
<script>
    Vue.component("file-overview", {
        template: "#file-overview",
        data: () => ({
            data: [],
        }),
        created() {
            fetch("/api/files")
                .then(res => res.json())
                .then(res => this.data = res)
                .catch(() => alert("Error while fetching files"));
        }
    });
</script>