# OpenAI e Z.ai — autenticação Maxx

## Implementado

Seleção OpenRouter/OpenAI/Z.ai em Settings, chaves cifradas separadas com Android Keystore, modelos separados, desligar apenas o fornecedor escolhido e links para gerir chaves no browser. O pedido de aprovação mostra o fornecedor real. O consentimento liga digest, fornecedor, modelo e validade; mudar o destinatário invalida-o. Os destinos são fixos e redirecionamentos HTTP não são seguidos.

OpenAI usa `https://api.openai.com/v1/chat/completions` e `max_completion_tokens`. Z.ai usa a API geral `https://api.z.ai/api/paas/v4/chat/completions`; não usa a quota do Coding Plan. OpenRouter conserva o formato e a chave antigos. Não há credenciais embutidas no APK ou publicadas no repositório. BYOK é uma escolha de preview pessoal: uma chave no aparelho continua sujeita ao compromisso desse aparelho; não distribuir uma chave partilhada de produção dentro da app.

Os modelos são indicados pelo utilizador conforme o acesso da sua conta. A app não anuncia como validada uma ligação só por guardar a chave. A primeira mensagem Maxx, após aprovação do contexto, confirma autenticação e acesso ao modelo. Streaming parcial/interrompido e respostas vazias são erros, não sucesso. Cloud permanece text-only nesta versão; as ferramentas locais continuam no Tiny.

## OAuth: pedido ainda pendente

O utilizador pediu o fluxo de browser de Codex/Z Code, com conta já autenticada e API key opcional. Confirmámos que esse fluxo existe nesses produtos. Não implementámos um falso login que apenas abre o site: ainda falta uma integração suportada que devolva e renove tokens para o Paladino.

A documentação do Codex descreve autenticação gerida pelo Codex/app-server; a do Z Code descreve a conta vinculada ao próprio Z Code. Estas fontes não estabelecem um registo de cliente Android Paladino. O código público do Codex usa callback localhost e PKCE; reutilizar esse processo requer decidir entre um runtime Codex integrado/ponte autenticada e um cliente autorizado independente. Não lemos nem copiámos tokens da sessão Codex do utilizador. Não criámos um proxy ou expusemos um servidor sem uma arquitetura definida.

Falta para OAuth: contrato de cliente/redirect aceites pelo fornecedor, browser externo, PKCE S256 e state de uso único, receção segura do callback, isolamento de tokens por fornecedor, renovação/expiração, cancelamento e revogação. Permissão do utilizador para abrir o browser não resolve, por si só, o registo e o retorno de tokens ao nosso cliente. Esta pendência está explícita e não é apresentada como concluída.

## Evidência e testes

Testes JVM verificam formato por fornecedor, binding de consentimento, rejeição de troca de fornecedor antes de enviar rede, bloqueio de redirects e sanitização de erros. Instrumentação usa chaves sintéticas num ficheiro isolado para confirmar cifra, persistência e remoção independente. O picker é exercitado na UI. Estes testes não substituem autenticação e inferência com uma conta real.

O teste live exige introduzir a chave diretamente no campo protegido da app, escolher um modelo acessível e aprovar uma mensagem de teste em Maxx. Não enviar chaves nesta conversa. Sem esse passo, o resultado deve ser reportado como integração testada localmente, autenticação live pendente.

## Fontes primárias consultadas em 8 de setembro de 2026

- [OpenAI API authentication](https://developers.openai.com/api/reference/overview): credenciais bearer; recomenda não expor chaves em código cliente.
- [Codex authentication](https://developers.openai.com/codex/auth): login por browser e API key no Codex.
- [Codex login implementation](https://github.com/openai/codex/blob/main/codex-rs/login/src/server.rs): PKCE, callback e troca de tokens no cliente Codex.
- [Z.ai API introduction](https://docs.z.ai/api-reference/introduction): bearer API key e endpoint geral.
- [Z.ai chat completion](https://docs.z.ai/api-reference/llm/chat-completion): stream e parâmetros de geração; modelos reasoning podem consumir o orçamento antes de produzir texto final.
- [Z Code Connect Models](https://zcode.z.ai/en/docs/configuration): login por conta, API key e separação entre endpoint Coding Plan e API geral.
