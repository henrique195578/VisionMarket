(function () {
'use strict';
const atual = document.querySelector('meta[name="vision-build"]')?.content;
const aviso = document.getElementById('avisoAtualizacao');
const botao = document.getElementById('atualizarAplicacao');
if (!atual || !aviso || !botao) return;
let consultando = false, nova = null;
async function verificar() {
    if (consultando || document.hidden || navigator.onLine === false) return;
    consultando = true;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    try {
        const resposta = await fetch('/api/versao', { cache: 'no-store', signal: controller.signal });
        if (!resposta.ok || resposta.redirected) return;
        const dados = await resposta.json();
        if (typeof dados.build !== 'string' || !dados.build) return;
        nova = dados.build === atual ? null : dados.build;
        aviso.hidden = !nova;
    } catch (_) {
        // Falha de rede mantém a tela atual utilizável.
    } finally { clearTimeout(timeout); consultando = false; }
}
botao.addEventListener('click', () => {
    if (!nova) return;
    if (!window.confirm('Atualizar agora? Salve ou conclua os dados em preenchimento antes de continuar.')) return;
    const url = new URL(location.href); url.searchParams.set('versao', nova);
    location.replace(url.toString());
});
setInterval(verificar, 60000);
window.addEventListener('online', verificar);
window.addEventListener('focus', verificar);
document.addEventListener('visibilitychange', verificar);
verificar();
})();
