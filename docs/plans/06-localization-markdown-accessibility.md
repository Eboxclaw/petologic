# PT/EN, Markdown e acessibilidade — implementação

## Âmbito

Recursos Android em português/inglês para a interface, seleção de idioma por app no Android 13+, CommonMark nativo nas respostas e bolhas, e controlos que continuam acessíveis com letra ampliada. Histórico, nomes técnicos, instruções e conteúdo escrito pelo utilizador permanecem intactos.

## Decisões

- `values/strings.xml` e `values-pt/strings.xml`, com catálogo de identificadores estáveis. As chaves de navegação e de ferramentas não são traduzidas. `UiText` consulta recursos apenas nas superfícies de interface, com formatos posicionais para rótulos variáveis.
- `localeConfig` declara inglês e português. Android 12 segue o idioma do aparelho; não há uma preferência paralela escondida.
- Markwon 4.6.2: CommonMark, tabelas, tarefas e rasurado. Renderização em TextView com seleção de texto; sem WebView, execução HTML ou carregamento automático de imagens. O texto alternativo das imagens é preservado. Links HTTP/HTTPS sem credenciais só abrem após toque; outros esquemas são rejeitados.
- Mensagens do utilizador continuam em texto literal. Respostas sem formatação mantêm o componente Compose; respostas formatadas usam spans nativos. O texto original guardado nunca é reescrito para corrigir Markdown malformado.
- A bolha é compacta e tem acesso à conversa completa; o painel pode deslocar-se verticalmente quando a altura disponível é curta. Linhas de chips adaptam-se à largura. A navegação mantém nomes acessíveis mesmo quando uma legenda visual precisa de reticências.

## Testes e limites

Verificar: recursos/formatação PT/EN, spans Markdown e texto alternativo, rejeição de URLs perigosos, regressão de ferramentas reais nas duas superfícies, PT com letra normal e ampliada no emulador, assinatura/atualização do APK. Não declarar validação física ou TalkBack completa sem um aparelho e evidência. Logs técnicos e erros não reconhecidos de bibliotecas podem manter o texto original em inglês.

## Fontes

- [Android — idiomas por app](https://developer.android.com/guide/topics/resources/app-languages)
- [Markwon — módulos e instalação](https://noties.io/Markwon/docs/v4/install.html)
- [Markwon — plugins](https://noties.io/Markwon/docs/v4/core/plugins.html)
- [Android — acessibilidade](https://developer.android.com/guide/topics/ui/accessibility/apps)

[Passo a passo de instalação](../INSTALL-ANDROID.md).
