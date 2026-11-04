// Regras de lint do app, com a configuração oficial do Expo (doc 3 §B.2).
const { defineConfig } = require("eslint/config");
const expoConfig = require("eslint-config-expo/flat");

module.exports = defineConfig([expoConfig, { ignores: ["dist/*", ".expo/*"] }]);
