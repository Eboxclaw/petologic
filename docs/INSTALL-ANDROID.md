# Instalar ou atualizar o Paladino no Android

## 1. Descarregar

1. No telemóvel, abre [petologic.vercel.app](https://petologic.vercel.app).
2. Toca em **DOWNLOAD APK**. O APK é para Android 12 ou superior e processadores ARM64.
3. Abre o ficheiro descarregado. Se o Android pedir autorização, permite **Instalar apps desconhecidas** apenas ao browser ou gestor de ficheiros que estás a usar.
4. Confirma **Instalar** ou **Atualizar**. Não é necessário desativar o Play Protect.

Também podes usar a [página oficial de versões](https://github.com/Eboxclaw/petologic-downloads/releases). O ficheiro SHA256SUMS.txt acompanha cada APK para verificar a integridade.

## 2. Se já tens uma versão anterior

**Exceção única — atualizar para a 0.1.6:** a assinatura mudou (a chave original perdeu-se), por isso quem tem a **0.1.5 ou anterior tem de desinstalar primeiro** e instalar a 0.1.6 de seguida. Esse passo apaga as conversas e o modelo descarregado; a partir da 0.1.6, usa **Definições → Cópia pessoal → Guardar cópia** antes de qualquer troca para poderes restaurar depois.

**Nas restantes atualizações (0.1.6 em diante):** **Não atualiza automaticamente.** Abre **Definições → Atualizações da app → Atualizar app**. O botão abre os downloads oficiais no browser; escolhe o APK mais recente e confirma a atualização no Android. As versões públicas assinadas a partir da 0.1.6 usam a mesma identidade e podem ser atualizadas por cima: **não desinstales primeiro**. As conversas, notas e modelos devem manter-se. O percurso de atualização é testado com uma sessão no emulador; guarda à parte informação insubstituível antes de atualizar uma prévia (ou usa a Cópia pessoal).

Um APK de desenvolvimento/debug usa outra assinatura e não pode ser atualizado diretamente pelo APK público. Se aparecer “app não instalada” ou conflito de assinatura, não apagues os dados por tentativa: confirma primeiro qual a versão instalada.

## 3. Preparar o modelo local

1. Abre **Definições / Settings**.
2. Na biblioteca de modelos, descarrega **LFM2.5-350M Q4_K_M** (cerca de 229 MB). Espera pela verificação e pelo estado pronto.
3. Se já tens o ficheiro exato numa pasta acessível, escolhe **Usar ficheiro existente / Use existing file** ou **Escolher pasta / Choose folder**. A app verifica o modelo e a quantização antes de reutilizar; não lê os ficheiros privados de outras apps.
4. Volta ao **Chat**, escolhe **Tiny** e escreve “Olá, como estás?”. Depois da instalação do modelo, o Tiny funciona offline.

Se enviares uma mensagem em Tiny antes de teres o modelo escolhido instalado, aparece um cartão com **Instalar modelo**. A mensagem fica à espera enquanto a app está aberta. Após instalar ou importar, toca em **Continuar conversa**; o download não envia a mensagem sozinho.

A memória semântica usa um download opcional separado. O modelo local não vai dentro do APK e não precisa de ser descarregado novamente só porque atualizas a app.

## 4. Português ou inglês

A app segue o idioma do telemóvel, com recursos em português e inglês. No Android 13 ou superior, abre **Definições → Idioma → Alterar idioma da app** e escolhe o idioma. No Android 12, usa o idioma do sistema.

Esta opção altera a interface. Não traduz retroativamente mensagens guardadas, nomes de modelos, chaves, instruções escritas por ti ou registos técnicos. Podes conversar em português ou inglês independentemente do idioma da interface.

## 5. Ativar o Sprite flutuante

1. Abre **Definições → Sprite e widget → Gerir Sprite e widget**.
2. Toca em **Ativar Sprite flutuante**.
3. Autoriza **Apresentar sobre outras apps** no ecrã do Android. A app pode também pedir notificações para mostrar o controlo de paragem.
4. Volta ao ecrã inicial: toca no Paladino para abrir a bolha e arrasta-o para mudar de posição.
5. Usa **Nova conversa** para começar outra sessão ou **Conversa completa** para ler o histórico na app. **Abrir para aprovar** leva ao pedido de aprovação, sem executar a ação automaticamente.
6. Para parar, usa a notificação do Sprite ou regressa às mesmas definições.

A bolha mostra uma resposta compacta; o histórico completo fica no Chat. Algumas superfícies protegidas do Android não permitem sobreposições. O widget do ecrã inicial é uma alternativa estática que abre o Chat.

## 6. Maxx é opcional

Em Definições podes ligar uma chave API de OpenRouter, OpenAI ou Z.ai e indicar um modelo disponível na tua conta. Introduz a chave apenas no campo protegido da app. Cada pedido cloud exige revisão; o fornecedor pode cobrar pela utilização. OAuth pelo browser ainda não está ligado. Tiny não exige conta nem chave.

## 7. Verificações no teu telemóvel

- Confirma que o Chat e a bolha respondem e guardam a conversa certa.
- Muda entre português e inglês e abre os menus.
- Aumenta a letra nas definições Android: verifica se consegues enviar, parar, fechar a bolha e abrir a conversa completa.
- Experimenta rotação, teclado aberto e TalkBack. Se um controlo ficar escondido, envia uma captura e indica o modelo do telemóvel/versão Android.

Os testes em emulador não substituem esta validação num aparelho físico.

## 8. Leituras do telemóvel (0.1.5+)

Em **Definições → Acesso ao telemóvel → Gerir acesso**, permite a leitura do calendário se a quiseres usar. Só aparecem calendários sincronizados com o Android. Para meteorologia, indica a cidade e ativa Open-Meteo; a cidade/coordenadas saem do aparelho apenas quando fazes o pedido.

Experimenta: “Que horas são?”, “Qual é o próximo alarme?”, “Mostra a minha agenda”, “Meteorologia”. São leituras diretas: não criam alarmes, não alteram eventos e não ligam uma conta de email.

Se a sobreposição disser **O acesso foi negado à app**, abre **Informações da app → ⋮ → Permitir definições restritas**, se disponível e se confiares na instalação oficial. Volta à autorização de **Sobrepor a outras apps** e tenta novamente. A app inclui **Autorização bloqueada? → Abrir informações da app no Android**. Não desatives o Play Protect; num aparelho gerido a autorização pode continuar proibida. Usa a bolha interna enquanto resolves a autorização.
