import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/setupTests.ts",
    coverage: {
      provider: "v8",
      // SonarQube reads coverage/lcov.info. The text report is for the terminal.
      reporter: ["text", "lcov"],
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/setupTests.ts", "src/main.tsx"],
    },
  },
});
