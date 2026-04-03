# Rigrise AI Edge ✨

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Run powerful open-source LLMs directly on your Android device — fully offline, private, and fast.**

Rigrise AI Edge is an experimental app that puts cutting-edge Generative AI models in your hands, running entirely on-device without needing an internet connection once the model is loaded.

> **Attribution**: This project is a fork of [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) by Google. All original work, architecture, and core features are credited to the Google AI Edge team. This fork adds additional features and customizations on top of the original. We are grateful to Google for open-sourcing this project under the Apache 2.0 License.

**Now Featuring: Gemma 4**

Official support for the Gemma 4 family — experience advanced reasoning, logic, and creative capabilities without ever sending your data to a server.

## ✨ Core Features

* **Agent Skills**: Augment your LLM with tools like Wikipedia, interactive maps, and rich visual summary cards. Load modular skills from a URL or browse community contributions.
* **AI Chat with Thinking Mode**: Multi-turn conversations with optional step-by-step reasoning visibility.
* **Ask Image**: Identify objects, solve visual puzzles, or get descriptions using your camera or photo gallery.
* **Audio Scribe**: Transcribe and translate voice recordings into text on-device.
* **Prompt Lab**: Test prompts and single-turn use cases with control over temperature, top-k, and other parameters.
* **Mobile Actions**: Offline device controls powered by a finetune of FunctionGemma 270m.
* **Tiny Garden**: Experimental mini-game using natural language to plant and harvest a virtual garden.
* **Model Management & Benchmark**: Download models from the list or load your own. Run benchmarks to measure performance on your hardware.
* **100% On-Device Privacy**: All inference happens locally. No internet required.

## 🏁 Get Started

1. **Requirement**: Android 12 and up
2. **Build locally**: See [DEVELOPMENT.md](DEVELOPMENT.md) for build instructions
3. **Install via ADB**: `adb install app-debug.apk`

## 🛠️ Technology

* **LiteRT**: Lightweight runtime for optimized model execution
* **LLM Inference API**: On-device Large Language Model inference
* **Hugging Face Integration**: Model discovery and download

## ⌨️ Development

Check out the [development notes](DEVELOPMENT.md) for instructions on how to build the app locally.

## 🤝 Feedback

* 🐞 **Found a bug?** Open an issue in this repository
* 💡 **Have an idea?** Submit a feature request

## 📄 License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

This project is based on [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery), copyright Google LLC, licensed under the Apache License, Version 2.0.
