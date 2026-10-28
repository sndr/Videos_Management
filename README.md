# Videos Management Studio

Aplicativo desktop em Java para visualizar e gerenciar vídeos do canal autenticado no YouTube. O login usa OAuth 2.0 do Google e os vídeos e métricas são carregados pela YouTube Data API v3; o app não usa uma lista de demonstração para a conta conectada.

## Recursos

- Login com uma conta Google pelo navegador.
- Lista de vídeos da playlist de uploads do canal, inclusive vídeos privados que a conta autenticada pode acessar.
- Pesquisa e filtros por status, como **Privado**, **Publicado** e **Não Listado**.
- Ação em **Detalhes** para tornar um vídeo público ou privado, com confirmação antes de atualizar o YouTube.
- Painel de métricas calculado a partir dos vídeos carregados.
- Modo convidado para desenvolvimento. Ele abre o dashboard sem conectar ao Google e sem carregar vídeos reais.

## Requisitos

- Java 11 ou superior.
- IntelliJ IDEA ou outra IDE Java.
- Biblioteca FlatLaf 3.5.4. A configuração IntelliJ deste repositório aponta para `com.formdev:flatlaf:3.5.4`.
- Projeto Google Cloud com OAuth 2.0 configurado e a YouTube Data API v3 habilitada.
- Para editar clipes: FFmpeg e FFprobe instalados. No Windows, pode-se usar `winget install --id Gyan.FFmpeg --exact`.

## Executar pela IntelliJ IDEA

1. Abra a pasta raiz do repositório como um projeto IntelliJ.
2. Configure um JDK 11 ou superior para o projeto e confirme que a biblioteca FlatLaf foi resolvida.
3. Abra `src/Main.java` e execute `Main.main()`.
4. Use **Continuar com o Google** para conectar um canal ou **Entrar como convidado** para explorar a interface sem dados reais.

## Configurar Google Cloud e YouTube

1. Crie ou selecione um projeto no [Google Cloud Console](https://console.cloud.google.com/).
2. Habilite a [YouTube Data API v3](https://console.cloud.google.com/apis/library/youtube.googleapis.com) nesse projeto. Ele precisa ser o mesmo projeto associado ao cliente OAuth.
3. Configure a tela de consentimento OAuth como Externo. Enquanto o app estiver no estado **Testando**, adicione a conta Google que fará o login à lista de usuários de teste. Consulte as [regras de público e usuários de teste do Google](https://support.google.com/cloud/answer/15549945?hl=pt-BR).
4. Crie um cliente OAuth do tipo **Aplicativo para computador (Desktop app)** e baixe o arquivo JSON `client_secret_*.json`.
5. Abra o app, escolha **Configurar Credenciais do Google Cloud Console** e importe esse JSON.
6. Faça login e aceite as permissões apresentadas pelo Google.

O app abre um retorno local em `127.0.0.1` usando uma porta temporária. Não é necessário cadastrar uma URL fixa de callback para esse fluxo de aplicativo desktop.

Para alterar a visibilidade de um vídeo, o app solicita o escopo `youtube.force-ssl`, exigido pelo método [`videos.update`](https://developers.google.com/youtube/data/reference/rest/v3/videos/update). Essa permissão é mais ampla que somente leitura: o Google informa que ela permite ver, editar e excluir permanentemente conteúdo do YouTube, além de ratings, comentários e legendas ([descrição do escopo](https://developers.google.com/identity/protocols/oauth2/scopes)). Autorize-a somente se quiser usar os controles de visibilidade. Se o app já tinha uma sessão anterior, desconecte e entre novamente para conceder o novo escopo.

## Usar o dashboard

1. Entre com a conta Google que possui o canal desejado.
2. Aguarde a carga dos vídeos da playlist de uploads. Use o campo de pesquisa e o filtro de status para localizar um vídeo privado.
3. Selecione o vídeo e abra **Detalhes**.
4. Escolha **Tornar público** ou **Tornar privado** e confirme a alteração. O status na tabela muda após a confirmação do YouTube.

O modo convidado é apenas para desenvolvimento: ele não consulta o YouTube e não pode alterar a visibilidade dos vídeos.

**Limitação:** os botões **Novo Vídeo**, **Editar** e **Excluir** ainda não fazem upload, atualização de metadados ou exclusão no YouTube. Essas ações só alteram a lista local da sessão. O botão de visibilidade em **Detalhes** é a ação que atualiza o vídeo real no YouTube.

## Cortar clipes locais

1. Instale o [FFmpeg](https://ffmpeg.org/download.html), incluindo FFmpeg e FFprobe. No Windows, também pode instalar com `winget install --id Gyan.FFmpeg --exact`. Deixe os executáveis no `PATH`, ou use **Localizar FFmpeg** para escolher `ffmpeg.exe`; o app procura `ffprobe.exe` na mesma pasta. O caminho fica salvo nas preferências locais do aplicativo.
2. No dashboard, abra **Editor de Clipes** e selecione o arquivo original que está salvo no computador.
3. Informe início e fim no formato `HH:MM:SS`. O arquivo fonte e o trecho selecionado devem ter no máximo dez minutos.
4. Escolha o destino e exporte. O vídeo é recodificado em MP4 (H.264, CRF 18) mantendo as dimensões do arquivo fonte; o áudio é convertido para AAC 192 kb/s. O arquivo original não é alterado.

O corte usa recodificação para manter alta qualidade e permitir cortes precisos. O resultado é visualmente próximo do original, mas a recodificação pode alterar ligeiramente a imagem e o áudio. A resolução não é ampliada nem reduzida: é preservada conforme as dimensões detectadas pelo FFprobe. Use o arquivo fonte original para obter a melhor qualidade disponível. O dashboard não baixa vídeos do YouTube e a API não fornece o arquivo original enviado ao canal.

## Credenciais e sessão local

- As credenciais OAuth importadas são guardadas em `.google_oauth_credentials.properties` dentro da pasta pessoal do usuário do sistema, fora do repositório.
- Se **Lembrar login Google** estiver marcado, a sessão OAuth é guardada em `.videos_management_session.properties`, também na pasta pessoal. A sessão local considera o token válido por cerca de 55 minutos e não renova o token automaticamente; após expirar, faça login novamente. No modo de teste do Google Cloud, a autorização dos usuários de teste também expira após sete dias.
- O modo convidado não grava uma sessão Google.
- Os arquivos de credenciais e sessão são propriedades locais e não são criptografados pelo app. Mantenha-os na pasta pessoal, não os adicione ao Git e não compartilhe o Client Secret ou tokens.

## Solução de problemas

- **HTTP 403 / API não configurada:** confirme que a YouTube Data API v3 está habilitada no mesmo projeto Cloud do cliente OAuth.
- **Acesso bloqueado durante o login:** confirme que o app está configurado como Externo e que a conta está na lista de usuários de teste enquanto o app estiver em Testando.
- **Permissão insuficiente ao alterar visibilidade:** desconecte, entre novamente e aceite a permissão adicional do YouTube.
- **Nenhum vídeo encontrado:** confirme que entrou na conta Google correta e que ela possui um canal do YouTube. Um item removido ou indisponível pode existir na playlist de uploads sem retornar detalhes de vídeo.

Para mais informações, consulte o [guia inicial da YouTube Data API](https://developers.google.com/youtube/data/getting-started).
