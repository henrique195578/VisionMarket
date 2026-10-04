(function () {
'use strict';
const $ = id => document.getElementById(id);
const moeda = n => Number(n || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
let arquivo = '', codigoQr = '', preview = null, lendo = false, salvando = false, importada = false;
let cameraStream = null, cameraTimer = null, cameraGeracao = 0;
function numero(value) {
    const texto = String(value || '').trim();
    return Number(texto.includes(',') ? texto.replace(/\./g, '').replace(',', '.') : texto);
}
function campo(rotulo, nome, valor, tipo, step) {
    const div = document.createElement('div'); div.className = nome === 'nome' ? 'col-12' : 'col-6 col-md-4';
    const label = document.createElement('label'); label.className = 'form-label small'; label.textContent = rotulo;
    const input = document.createElement('input'); input.className = 'form-control'; input.dataset.campo = nome;
    input.type = tipo || 'text'; input.value = valor == null ? '' : valor; input.setAttribute('aria-label', rotulo);
    if (tipo === 'number') { input.min = '.0001'; input.step = step || '.0001'; input.required = true; }
    else input.maxLength = nome === 'nome' ? 150 : 100;
    if (nome === 'nome' || nome === 'unidadeMedida') input.required = true;
    div.append(label, input); return div;
}
function adicionar(item) {
    if (importada || salvando) return;
    item = item || { nome: '', quantidade: 1, unidadeMedida: 'UN', precoUnitario: '', total: '' };
    const card = document.createElement('div'); card.className = 'border rounded-3 p-3 mb-3 nota-item';
    const row = document.createElement('div'); row.className = 'row g-2';
    row.append(campo('Nome do produto', 'nome', item.nome), campo('Marca (opcional)', 'marca', item.marca),
        campo('Embalagem / peso (opcional)', 'peso', item.peso), campo('Código de barras (opcional)', 'codigoBarras', item.codigoBarras),
        campo('Quantidade', 'quantidade', item.quantidade, 'number'), campo('Unidade (UN, KG…)', 'unidadeMedida', item.unidadeMedida),
        campo('Preço unitário pago (R$)', 'precoUnitario', item.precoUnitario, 'number'),
        campo('Total pago neste item (R$)', 'total', item.total, 'number', '.01'));
    const aviso = document.createElement('p'); aviso.className = 'small text-warning-emphasis mt-2 mb-1 nota-aviso'; aviso.textContent = item.aviso || '';
    const remover = document.createElement('button'); remover.type = 'button'; remover.className = 'btn btn-sm btn-outline-danger';
    remover.textContent = 'Remover item'; remover.addEventListener('click', () => { card.remove(); resumo(); });
    card.append(row, aviso, remover); $('notaItens').append(card);
    card.addEventListener('input', () => { $('notaConferida').checked = false; resumo(); }); resumo();
}
function coletar() {
    return [...document.querySelectorAll('.nota-item')].map(card => {
        const item = {};
        card.querySelectorAll('[data-campo]').forEach(el => {
            item[el.dataset.campo] = ['quantidade', 'precoUnitario', 'total'].includes(el.dataset.campo) ? numero(el.value) : el.value.trim();
        });
        return item;
    });
}
function resumo() {
    const itens = coletar(); let soma = 0;
    document.querySelectorAll('.nota-item').forEach((card, i) => {
        const item = itens[i]; soma += item.total || 0;
        card.querySelector('.nota-aviso').textContent = Math.abs(item.quantidade * item.precoUnitario - item.total) > .03
            ? 'Quantidade × preço difere do total. Confira descontos ou corrija os valores antes de salvar.' : '';
    });
    $('notaResumo').textContent = itens.length + ' itens · Soma dos itens: ' + moeda(soma);
    $('notaImportar').disabled = !arquivo || !itens.length || lendo || salvando || importada || !!cameraStream;
}
function fontesDisabled(valor) {
    document.querySelectorAll('.nota-fonte').forEach(el => { el.disabled = valor; });
    $('notaAdicionar').disabled = valor || importada;
}
function iniciarLeitura(mensagem) {
    if (lendo || salvando) return false;
    pararCamera(); arquivo = ''; codigoQr = ''; importada = false; lendo = true;
    $('notaForm').querySelectorAll('input,select,button').forEach(el => { el.disabled = false; });
    $('notaItens').replaceChildren(); $('notaConferida').checked = false; $('notaTextoDetalhes').hidden = true;
    $('notaSucesso').hidden = true; $('notaComparar').hidden = true; $('notaOrigem').hidden = true;
    $('notaImportar').textContent = 'Salvar produtos e preços pagos';
    $('notaStatus').textContent = mensagem; fontesDisabled(true); resumo(); return true;
}
function terminarLeitura() { lendo = false; fontesDisabled(false); resumo(); }
async function respostaJson(response) {
    if (response.redirected) throw new Error('Recarregue a página para renovar a sessão.');
    let dados; try { dados = await response.json(); } catch (_) { throw new Error('O servidor não respondeu à leitura. Tente novamente.'); }
    if (!response.ok) throw new Error(dados.erro || 'Não foi possível consultar a nota.');
    return dados;
}
function exibirLeitura(dados) {
    document.dispatchEvent(new CustomEvent("vision:nota-lida", {detail: dados}));
    arquivo = dados.arquivo; codigoQr = dados.codigoQr || '';
    $('notaTexto').textContent = dados.texto || ''; $('notaQrTexto').textContent = codigoQr ? 'Link / QR: ' + codigoQr : 'Nenhum QR reconhecido.';
    $('notaTextoDetalhes').hidden = false; $('notaStatus').textContent = dados.aviso;
    if (dados.dataCompra) $('notaData').value = dados.dataCompra.slice(0,16);
    if (dados.emitente && /SENDAS|ASSA[IÍ]/i.test(dados.emitente)) $('notaMercado').value = 'ASSAI';
    if (dados.emitente) { $('notaOrigem').textContent = 'Emitente da nota: ' + dados.emitente + '. Confira o mercado e a filial abaixo.'; $('notaOrigem').hidden = false; }
    (dados.itens || []).forEach(adicionar); if (!dados.itens || !dados.itens.length) adicionar();
    if (dados.urlConsulta) atualizarLink(dados.urlConsulta);
}
function linkOficial(valor) {
    try {
        const url = new URL(valor.trim());
        const hosts = ['www.nfce.fazenda.sp.gov.br','nfce.fazenda.sp.gov.br','sat.fazenda.sp.gov.br','satsp.fazenda.sp.gov.br',
            'www.nfp.fazenda.sp.gov.br','nfp.fazenda.sp.gov.br','www.fazenda.sp.gov.br','fazenda.sp.gov.br'];
        return ['http:', 'https:'].includes(url.protocol) && hosts.includes(url.hostname.toLowerCase()) && !url.username && !url.password
            && (!url.port || ['80','443'].includes(url.port)) ? url.href : null;
    } catch (_) { return null; }
}
function atualizarLink(valor) {
    $('notaLink').value = valor; const url = linkOficial(valor);
    $('notaAbrirConsulta').hidden = !url; if (url) $('notaAbrirConsulta').href = url;
}
async function consultarLink(valor) {
    const url = linkOficial(valor);
    if (!url) { $('notaStatus').textContent = 'Use o link oficial de consulta da nota paulista, como o endereço do QR Code.'; return; }
    if (!iniciarLeitura('Consultando os itens no portal fiscal…')) return;
    atualizarLink(url); $('notaPreview').hidden = true;
    try {
        const response = await fetch('/api/notas/link', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({url}) });
        exibirLeitura(await respostaJson(response));
    } catch (err) { $('notaStatus').textContent = err.message; }
    finally { terminarLeitura(); }
}
document.addEventListener('vision:nota-chave', async event => {
    if (!iniciarLeitura('Consultando a chave no portal fiscal…')) return;
    $('notaPreview').hidden = true;
    try {
        const response = await fetch('/api/notas/chave', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({chave:event.detail})});
        exibirLeitura(await respostaJson(response));
    } catch (err) { $('notaStatus').textContent = err.message; }
    finally { terminarLeitura(); }
});
$('notaConsultarLink').addEventListener('click', () => consultarLink($('notaLink').value));
$('notaLink').addEventListener('input', () => atualizarLink($('notaLink').value));
$('notaLink').addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); consultarLink($('notaLink').value); } });
$('notaAdicionar').addEventListener('click', () => adicionar());
$('notaFoto').addEventListener('change', async () => {
    const file = $('notaFoto').files[0]; if (!file || !iniciarLeitura('Recebendo o arquivo e reconhecendo os itens…')) return;
    if (preview) URL.revokeObjectURL(preview);
    const imagem = file.type.startsWith('image/') || /\.(png|jpe?g|webp)$/i.test(file.name);
    $('notaPreview').hidden = !imagem;
    if (imagem) { preview = URL.createObjectURL(file); $('notaPreview').src = preview; }
    const body = new FormData(); body.append('file', file);
    try { exibirLeitura(await respostaJson(await fetch('/api/notas/ler', { method: 'POST', body }))); }
    catch (err) { $('notaStatus').textContent = err.message; }
    finally { terminarLeitura(); }
});
function pararCamera() {
    cameraGeracao++; if (cameraTimer) clearTimeout(cameraTimer); cameraTimer = null;
    if (cameraStream) cameraStream.getTracks().forEach(track => track.stop());
    cameraStream = null; $('notaVideo').srcObject = null; $('notaCameraArea').hidden = true; resumo();
}
function quadroBlob(fonte) {
    const canvas = document.createElement('canvas');
    const largura = fonte.videoWidth || fonte.naturalWidth || fonte.width;
    const altura = fonte.videoHeight || fonte.naturalHeight || fonte.height;
    const escala = Math.min(1,1000/largura);
    canvas.width = Math.round(largura*escala); canvas.height = Math.round(altura*escala);
    canvas.getContext('2d').drawImage(fonte,0,0,canvas.width,canvas.height);
    return new Promise(resolve => canvas.toBlob(resolve,'image/jpeg',.85));
}
async function detectarServidor(blob) {
    const body = new FormData(); body.append('file',blob,'qr.jpg');
    const response = await fetch('/api/notas/qr', { method: 'POST', body });
    if (response.status === 204) return null;
    return (await respostaJson(response)).codigoQr || null;
}
$('notaAbrirCamera').addEventListener('click', async () => {
    if (lendo || salvando) return;
    if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
        $('notaStatus').textContent = 'A câmera ao vivo precisa de HTTPS no celular. Use Fotografar QR Code para abrir a câmera do aparelho.';
        $('notaQrFoto').click(); return;
    }
    pararCamera(); const geracao = cameraGeracao;
    $('notaStatus').textContent = 'Autorize a câmera e aponte para o QR da nota.';
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 } }, audio: false });
        if (geracao !== cameraGeracao || $('leitorqr').hidden || document.hidden) { stream.getTracks().forEach(t => t.stop()); return; }
        cameraStream = stream; $('notaVideo').srcObject = stream; $('notaCameraArea').hidden = false;
        await $('notaVideo').play(); resumo();
        let detector = null;
        if (window.BarcodeDetector) {
            try {
                const tipos = await window.BarcodeDetector.getSupportedFormats();
                if (tipos.includes('qr_code')) detector = new window.BarcodeDetector({formats:['qr_code']});
            } catch (_) {}
        }
        const lerQuadro = async () => {
            if (!cameraStream || geracao !== cameraGeracao) return;
            try {
                let codigo = null;
                if ($('notaVideo').readyState >= 2) {
                    if (detector) {
                        try { codigo = (await detector.detect($('notaVideo'))).find(c => c.rawValue)?.rawValue || null; }
                        catch (_) { detector = null; }
                    } else codigo = await detectarServidor(await quadroBlob($('notaVideo')));
                }
                if (geracao !== cameraGeracao) return;
                if (codigo) { pararCamera(); atualizarLink(codigo); await consultarLink(codigo); return; }
            } catch (err) {
                if (geracao !== cameraGeracao) return;
                pararCamera(); $('notaStatus').textContent = err.message + ' Tente Fotografar QR Code.'; return;
            }
            if (cameraStream && geracao === cameraGeracao) cameraTimer = setTimeout(lerQuadro, detector ? 350 : 1000);
        };
        if (geracao === cameraGeracao && cameraStream) cameraTimer = setTimeout(lerQuadro,350);
    } catch (err) {
        if (geracao !== cameraGeracao) return;
        pararCamera();
        $('notaStatus').textContent = err.name === 'NotAllowedError' ? 'A câmera não foi autorizada. Libere a permissão no navegador ou use Fotografar QR Code.'
            : 'Não foi possível abrir a câmera. Use Fotografar QR Code ou cole o link.';
    }
});
$('notaPararCamera').addEventListener('click', pararCamera);
document.addEventListener('vision:etapa', event => { if (event.detail !== 'leitorqr') pararCamera(); });
document.addEventListener('visibilitychange', () => { if (document.hidden) pararCamera(); });
window.addEventListener('pagehide', pararCamera);
$('notaQrFoto').addEventListener('change', async () => {
    const file = $('notaQrFoto').files[0]; if (!file || lendo || salvando) return;
    pararCamera(); lendo = true; fontesDisabled(true); resumo();
    $('notaStatus').textContent = 'Reconhecendo o QR da foto…';
    let codigo = null;
    try {
        const bitmap = await createImageBitmap(file);
        try { codigo = await detectarServidor(await quadroBlob(bitmap)); } finally { bitmap.close(); }
        if (!codigo) $('notaStatus').textContent = 'QR não reconhecido. Fotografe o código inteiro, com boa luz, ou cole o link.';
    } catch (err) { $('notaStatus').textContent = err.message; }
    finally { terminarLeitura(); $('notaQrFoto').value = ''; }
    if (codigo) { atualizarLink(codigo); await consultarLink(codigo); }
});
$('notaForm').addEventListener('submit', async event => {
    event.preventDefault(); if (lendo || salvando || importada || !arquivo) return;
    const itens = coletar();
    if (!itens.length || itens.some(i => !i.nome || !i.unidadeMedida || !(i.quantidade > 0) || !(i.precoUnitario > 0)
            || !(i.total > 0) || Math.abs(i.quantidade * i.precoUnitario - i.total) > .030001)) {
        $('notaStatus').textContent = 'Confira os nomes, quantidades, preços e totais de todos os itens.'; return;
    }
    pararCamera(); salvando = true; fontesDisabled(true); resumo(); $('notaImportar').textContent = 'Salvando itens…';
    const pedido = { arquivo, codigoQr, mercado: $('notaMercado').value, unidade: $('notaUnidade').value,
        dataCompra: $('notaData').value.slice(0,16) + ':00', adicionarNaLista: $('notaNaLista').checked, itens };
    $('notaForm').querySelectorAll('input,select,button').forEach(el => { el.disabled = true; });
    try {
        const dados = await respostaJson(await fetch('/api/notas/importar', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(pedido) }));
        importada = true; $('notaSucesso').textContent = dados.itensSalvos + ' itens salvos, com o preço unitário pago e a data da compra.';
        $('notaSucesso').hidden = false; $('notaComparar').href = '/?etapa=comparar&produto=' + dados.primeiroProdutoId;
        $('notaComparar').hidden = false; $('notaStatus').textContent = 'Nota importada com sucesso.';
    } catch (err) {
        $('notaStatus').textContent = err.message; $('notaForm').querySelectorAll('input,select,button').forEach(el => { el.disabled = false; });
    } finally {
        salvando = false; fontesDisabled(false); $('notaImportar').textContent = importada ? 'Nota importada' : 'Salvar produtos e preços pagos'; resumo();
    }
});
const agora = new Date(); agora.setMinutes(agora.getMinutes() - agora.getTimezoneOffset()); $('notaData').value = agora.toISOString().slice(0,16);
['notaMercado','notaUnidade','notaData'].forEach(id => $(id).addEventListener('change', () => { $('notaConferida').checked = false; }));
const linkInicial = new URLSearchParams(location.search).get('nota');
if (linkInicial) atualizarLink(linkInicial);
resumo();
})();