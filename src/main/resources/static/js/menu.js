(function () {
'use strict';
const menu = document.getElementById('menuLateral');
const botao = document.getElementById('alternarMenu');
if (!menu || !botao) return;
let aberto = !window.matchMedia('(max-width: 800px)').matches;
try {
    const salvo = localStorage.getItem('vision.menuAberto');
    if (salvo !== null) aberto = salvo === 'true';
} catch (_) {}
function mostrar(valor, salvar) {
    aberto = valor;
    menu.hidden = !aberto;
    document.body.classList.toggle('menu-aberto', aberto);
    botao.setAttribute('aria-expanded', String(aberto));
    botao.textContent = aberto ? 'Ocultar menu' : 'Mostrar menu';
    if (salvar) { try { localStorage.setItem('vision.menuAberto', String(aberto)); } catch (_) {} }
}
botao.addEventListener('click', () => mostrar(!aberto, true));
document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && aberto) { mostrar(false, true); botao.focus(); }
});
menu.querySelectorAll('[data-step]').forEach(item => item.addEventListener('click', () => {
    if (window.matchMedia('(max-width: 800px)').matches) { mostrar(false, true); botao.focus(); }
}));
mostrar(aberto, false);
})();
