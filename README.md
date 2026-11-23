Aplicativo Android nativo (Kotlin + Jetpack Compose) projetado para leitura de e-books (.epub e .pdf) com tradução 100% offline página por página, interface estilo Kindle otimizada para uso com uma mão e baixo consumo de memória RAM (compatível com Android 10+ / API 29).
O projeto está pré-configurado com GitHub Actions para compilar automaticamente o .apk na nuvem a cada push, permitindo baixar e instalar o aplicativo diretamente pelo navegador do celular sem precisar de Android Studio.

Principais Funcionalidades
​Tradução 100% Offline (Google ML Kit):
​Suporte aos 7 idiomas modernos mais leves (~30 MB por pacote): Português (pt), Inglês (en), Espanhol (es), Alemão (de), Francês (fr), Italiano (it) e Chinês (zh).
​Gerenciador de Idiomas: Visualização do tamanho exato em MB ocupado por pacote, download com opção de exigir Wi-Fi ou liberar dados móveis, e exclusão rápida para liberar espaço interno.
​ Tradução Sob Demanda (Zero Travamentos):
​Traduz em background (Dispatchers.IO) apenas a página atual e pré-carrega silenciosamente a página seguinte (página + 1), descartando páginas distantes da RAM.
​3 Modos de Visualização: Alternância instantânea no topo do leitor entre Só Tradução, Só Original e Bilíngue (original + tradução logo abaixo).
​Cache Persistente (Room / SQLite): Páginas já traduzidas ficam salvas localmente para abertura instantânea, com opção de limpeza de cache nas configurações.
​📱 Leitor Paginado Estilo Kindle:
​Paginação Real em Memória: Divide o texto em blocos exatos para a altura da tela usando TextMeasurer e mantém apenas ponteiros leves na RAM (sem carregar livros inteiros de uma vez).
​Navegação com Uma Mão: Toque na borda direita para avançar, borda esquerda para voltar e toque no centro para abrir/ocultar menus (além de suporte a gestos de deslizar).
​Conforto Visual: 4 temas (Papel, Sépia, Claro e Noturno AMOLED #000000), ajuste de tipografia, tamanho de fonte (14–28 sp) e espaçamento entre linhas.
​ Biblioteca e Marcadores:
​Importação de arquivos .epub e .pdf (com camada de texto) via seletor nativo (OpenDocument), sem exigir permissões invasivas de armazenamento.
​Sistema de marcadores para salvar páginas específicas e tela dedicada para retornar rapidamente aos trechos salvos.
​Arquitetura e Stack Tecnológica
​Linguagem & UI: Kotlin, Jetpack Compose, Material 3, Navigation Compose.
​Persistência: Room Database (livros, cache de traduções e marcadores) + DataStore (preferências de leitura).
​Tradução On-Device:
