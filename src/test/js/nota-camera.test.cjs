const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const script = fs.readFileSync('src/main/resources/static/js/notafiscal.js','utf8');
function ambiente(secure, getUserMedia) {
    const elements = new Map(), events = {}, timers = new Map(); let timer = 0;
    function el(id) {
        if (!elements.has(id)) elements.set(id, { id, hidden: false, value: '', dataset: {}, disabled: false, listeners: {},
            querySelectorAll: () => [], addEventListener(k, fn) { this.listeners[k] = fn; },
            click() { this.clicks = (this.clicks || 0) + 1; }, play: async () => {}, replaceChildren() {}, append() {} });
        return elements.get(id);
    }
    const document = { hidden: false, getElementById: el, querySelectorAll: () => [],
        addEventListener(k,fn) { events[k] = fn; }, createElement: () => el('created') };
    const window = { isSecureContext: secure, addEventListener(k,fn) { events[k] = fn; } };
    const context = { document, window, navigator: { mediaDevices: { getUserMedia } }, Date, URL, URLSearchParams, location: {search: ''}, console,
        setTimeout(fn) { timers.set(++timer,fn); return timer; }, clearTimeout(id) { timers.delete(id); } };
    vm.runInNewContext(script,context);
    return { el, events, timers };
}
test('abre a câmera traseira e encerra as trilhas ao fechar', async () => {
    let paradas=0, constraints;
    const stream={getTracks:()=>[{stop(){paradas++;}}]};
    const a=ambiente(true,async c=>{constraints=c;return stream;});
    await a.el('notaAbrirCamera').listeners.click();
    assert.equal(constraints.video.facingMode.ideal,'environment');
    assert.equal(a.el('notaVideo').srcObject,stream);
    assert.equal(a.el('notaCameraArea').hidden,false);
    a.el('notaPararCamera').listeners.click();
    assert.equal(paradas,1); assert.equal(a.el('notaVideo').srcObject,null);
    assert.equal(a.timers.size,0);
});
test('oferece a câmera do aparelho em acesso sem HTTPS', async () => {
    const a=ambiente(false,async()=>{throw Error('Não deve chamar getUserMedia');});
    await a.el('notaAbrirCamera').listeners.click();
    assert.equal(a.el('notaQrFoto').clicks,1);
    assert.match(a.el('notaStatus').textContent,/HTTPS/);
});
test('encerra a câmera ao trocar de menu', async () => {
    let paradas=0;
    const a=ambiente(true,async()=>({getTracks:()=>[{stop(){paradas++;}}]}));
    await a.el('notaAbrirCamera').listeners.click();
    a.events['vision:etapa']({detail:'lista'});
    assert.equal(paradas,1); assert.equal(a.timers.size,0);
});
test('descarta câmera autorizada depois de sair do leitor', async () => {
    let resolver, paradas=0;
    const a=ambiente(true,()=>new Promise(r=>{resolver=r;}));
    const abrindo=a.el('notaAbrirCamera').listeners.click();
    a.events['vision:etapa']({detail:'lista'});
    resolver({getTracks:()=>[{stop(){paradas++;}}]}); await abrindo;
    assert.equal(paradas,1); assert.equal(a.el('notaVideo').srcObject,null);
});
