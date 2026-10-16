module.exports = {
    uiPort: 1880,
    flowFile: "flows.json",
    credentialSecret: false,
    httpNodeCors: {
        origin: "*",
        methods: "GET,POST,PUT,DELETE,OPTIONS"
    },
    logging: {
        console: {
            level: "info",
            audit: false,
            metrics: false
        }
    }
}
