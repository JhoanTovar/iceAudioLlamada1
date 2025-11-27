import path from "path";
import { fileURLToPath } from "url";
import HtmlWebpackPlugin from "html-webpack-plugin";
import CopyWebpackPlugin from "copy-webpack-plugin";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default {
  mode: "development",
  entry: "./src/js/app.js",

  output: {
    path: path.resolve(__dirname, "dist"),
    filename: "bundle.js",
    clean: true,
  },

  module: {
    rules: [
      {
        test: /\.css$/i,
        use: ["style-loader", "css-loader"],
      },
    ],
  },

  plugins: [
    // ✔ Inyecta tu index.html desde la raíz
    new HtmlWebpackPlugin({
      template: "./index.html",
      filename: "index.html",
    }),

    // ✔ Copiar SOLO lo necesario
    new CopyWebpackPlugin({
      patterns: [
        // Copia estilos desde /src/styles
        { from: "src/styles", to: "styles" },

        // Copia Player
        { from: "Player.js", to: "Player.js" },

        // COPIA ICE
        {
          from: "node_modules/ice/lib/Ice.js",
          to: "js/Ice.js",
        },
      ],
    }),
  ],

  resolve: {
    fallback: {
      fs: false,
      net: false,
      tls: false,
    },
  },

  devServer: {
    static: {
      directory: path.join(__dirname, "dist"),
    },
    compress: true,
    port: 8080,
    hot: true,
    open: true,
  },
};
