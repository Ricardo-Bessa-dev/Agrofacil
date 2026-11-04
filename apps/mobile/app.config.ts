// Configuração do app Expo; a URL da API vem do ambiente para trocar entre dev, túnel e produção (doc 3 §C.1).
import type { ExpoConfig } from "expo/config";

const config: ExpoConfig = {
  name: "Agrofácil",
  slug: "agrofacil",
  version: "1.0.0",
  scheme: "agrofacil",
  orientation: "portrait",
  icon: "./assets/images/icon.png",
  // Uso no sol: só o tema claro de alto contraste (doc 3 §A.10).
  userInterfaceStyle: "light",
  ios: {
    icon: "./assets/expo.icon",
  },
  android: {
    package: "br.edu.ifba.agrofacil",
    adaptiveIcon: {
      backgroundColor: "#E6F4FE",
      foregroundImage: "./assets/images/android-icon-foreground.png",
      backgroundImage: "./assets/images/android-icon-background.png",
      monochromeImage: "./assets/images/android-icon-monochrome.png",
    },
    predictiveBackGestureEnabled: false,
  },
  web: {
    output: "server",
    favicon: "./assets/images/favicon.png",
  },
  // As permissões (GPS, câmera, microfone, digital) entram com os módulos no marco F2/F3.
  plugins: [
    "expo-router",
    [
      "expo-splash-screen",
      {
        backgroundColor: "#208AEF",
        image: "./assets/images/splash-icon.png",
        imageWidth: 76,
      },
    ],
  ],
  experiments: {
    typedRoutes: true,
    reactCompiler: true,
  },
  extra: {
    apiUrl: process.env.EXPO_PUBLIC_API_URL,
  },
};

export default config;
