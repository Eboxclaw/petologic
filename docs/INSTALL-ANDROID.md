# Instalar ou atualizar o Petologic no Android

O Petologic é uma prova de conceito: uma app Android com um agente de IA local (0xPaladino), verificações de antivírus, antimalware e anti spam, tudo a correr no telemóvel. Esta prévia é para testes; não substitui um antivírus completo.

## 1. Descarregar

1. No telemóvel, abre a página oficial: [https://eboxclaw-petologic.eitherbox.workers.dev](https://eboxclaw-petologic.eitherbox.workers.dev)
2. Toca em **DOWNLOAD PALADINO PREVIEW**. O APK é para Android 12 ou superior e processadores ARM64.
3. Abre o ficheiro descarregado. Se o Android pedir autorização, permite **Instalar apps desconhecidas** apenas ao browser ou gestor de ficheiros que estás a usar.
4. Confirma **Instalar** ou **Atualizar**. Não é necessário desativar o Play Protect.

Também podes usar as [versões oficiais no GitHub](https://github.com/Eboxclaw/petologic-downloads/releases). O ficheiro SHA256SUMS.txt acompanha cada versão para verificar a integridade antes de instalar.

## 2. Se já tens uma prévia anterior

As prévias antigas do Paladino (0.1.x a 0.2.2) podem usar uma assinatura diferente da nova versão 0.0.1. Se o Android recusar a atualização por conflito de assinatura:

1. Abre **Definições → Cópia pessoal → Guardar cópia** na app antiga para guardares conversas e notas.
2. Desinstala a app antiga.
3. Instala a nova 0.0.1 e restaura a cópia pessoal em **Definições → Cópia pessoal → Restaurar**.

Se é a primeira instalação, ignora este passo. Guarda à parte informação insubstituível antes de atualizar qualquer prévia.

## 3. Preparar o modelo local

1. Abre **Definições / Settings**.
2. Na biblioteca de modelos, descarrega **LFM3-Omni-2B Q4_K_M** (cerca de 1.1 GB). Espera pela verificação e pelo estado pronto.
3. Opcional: adiciona o **pacote de visão** (projetor, cerca de 516 MB) para o agente poder analisar uma imagem que escolhas, e o **d1-2B** (cerca de 1.1 GB) para sugestões de cloud mais inteligentes.
4. Se já tens o ficheiro exato numa pasta acessível, escolhe **Usar ficheiro existente / Use existing file** ou **Escolher pasta / Choose folder**. A app verifica o modelo e a quantização antes de reutilizar; não lê os ficheiros privados de outras apps.
5. Volta ao **Chat**, escolhe **Tiny** e escreve "Olá, como estás?". Depois da instalação do modelo, o Tiny funciona offline.

As verificações de segurança (mensagens, ligações, ficheiros, APKs) funcionam mesmo sem descarregares nenhum modelo.

## 4. Português ou inglês

A app segue o idioma do telemóvel, com recursos em português e inglês. No Android 13 ou superior, abre **Definições → Idioma → Alterar idioma da app** e escolhe o idioma. No Android 12, usa o idioma do sistema.

Esta opção altera a interface. Não traduz retroativamente mensagens guardadas, nomes de modelos, chaves, instruções escritas por ti ou registos técnicos. Podes conversar em português ou inglês independentemente do idioma da interface.

## 5. Ativar o Sprite flutuante

1. Abre **Definições → Sprite e widget → Gerir Sprite e widget**.
2. Toca em **Ativar Sprite flutuante**.
3. Autoriza **Apresentar sobre outras apps** no ecrã do Android. A app pode também pedir notificações para mostrar o controlo de paragem.
4. Volta ao ecrã inicial: toca no agente para abrir a bolha e arrasta-o para mudar de posição.
5. Usa **Nova conversa** para começar outra sessão ou **Conversa completa** para ler o histórico na app. **Abrir para aprovar** leva ao pedido de aprovação, sem executar a ação automaticamente.
6. Para parar, usa a notificação do Sprite ou regressa às mesmas definições.

A bolha mostra uma resposta compacta; o histórico completo fica no Chat. Algumas superfícies protegidas do Android não permitem sobreposições. O widget do ecrã inicial é uma alternativa estática que abre o Chat.

## 6. Maxx é opcional

Em Definições podes ligar uma chave API de OpenRouter, OpenAI ou Z.ai e indicar um modelo disponível na tua conta. Introduz a chave apenas no campo protegido da app. Cada pedido cloud exige revisão; o fornecedor pode cobrar pela utilização. OAuth pelo browser ainda não está ligado. Tiny não exige conta nem chave. Com o d1-2B instalado, o agente pode sugerir quando uma pergunta beneficia da cloud; a decisão é sempre tua.

## 7. Verificações no teu telemóvel

- Confirma que o Chat e a bolha respondem e guardam a conversa certa.
- Muda entre português e inglês e abre os menus.
- Aumenta a letra nas definições Android: verifica se consegues enviar, parar, fechar a bolha e abrir a conversa completa.
- Experimenta rotação, teclado aberto e TalkBack. Se um controlo ficar escondido, envia uma captura e indica o modelo do telemóvel/versão Android.
- No separador de segurança, partilha uma mensagem suspeita ou um ficheiro e lê a evidência apresentada.

Os testes em emulador não substituem esta validação num aparelho físico.

## 8. Leituras do telemóvel

Em **Definições → Acesso ao telemóvel → Gerir acesso**, permite a leitura do calendário se a quiseres usar. Só aparecem calendários sincronizados com o Android. Para meteorologia, indica a cidade e ativa Open-Meteo; a cidade/coordenadas saem do aparelho apenas quando fazes o pedido.

Experimenta: "Que horas são?", "Qual é o próximo alarme?", "Mostra a minha agenda", "Meteorologia". São leituras diretas: não criam alarmes, não alteram eventos e não ligam uma conta de email.

Se a sobreposição disser **O acesso foi negado à app**, abre **Informações da app → ⋮ → Permitir definições restritas**, se disponível e se confiares na instalação oficial. Volta à autorização de **Sobrepor a outras apps** e tenta novamente. A app inclui **Autorização bloqueada? → Abrir informações da app no Android**. Não desatives o Play Protect; num aparelho gerido a autorização pode continuar proibida. Usa a bolha interna enquanto resolves a autorização.

## 9. O que esta prova de conceito não faz

- Não é um antivírus completo: as regras incluídas são intencionamente limitadas e os updates chegam por pacotes assinados.
- Não remove aplicações, não bloqueia mensagens, não filtra chamadas e não oferece proteção VPN nesta versão.
- Uma verificação sem correspondências não garante que um ficheiro, app ou dispositivo seja seguro.
