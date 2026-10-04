(function(){
'use strict';
const $=id=>document.getElementById(id), leitor=$('leitorqr');
function tela(modo){
    $('notaPararCamera').click();
    if(modo) leitor.dataset.captura=modo; else delete leitor.dataset.captura;
    document.body.classList.toggle('nota-capturando',!!modo);
    $('notaManualArea').hidden=modo!=='manual';
    if(modo==='manual') $('notaStatus').textContent='';
}
function camera(){tela('camera');$('notaAbrirCamera').click();}
document.querySelectorAll('[data-nota-camera]').forEach(el=>el.addEventListener('click',camera));
$('notaDigitarManual').addEventListener('click',()=>{tela('manual');$('notaChave').focus();});
$('notaVoltarScanner').addEventListener('click',camera);
$('notaTentarCamera').addEventListener('click',()=>$('notaAbrirCamera').click());
$('notaOutrasOpcoes').addEventListener('click',()=>tela(null));
$('notaVoltarLista').addEventListener('click',()=>{tela(null);document.querySelector('[data-step="lista"]').click();});
function chaveValida(chave){
 if(!/^\d{44}$/.test(chave))return false;
 let soma=0,peso=2;for(let i=42;i>=0;i--){soma+=Number(chave[i])*peso;peso=peso===9?2:peso+1;}
 const resto=soma%11,digito=resto<2?0:11-resto;return digito===Number(chave[43]);
}
$('notaChave').addEventListener('input',()=>{
 const campo=$('notaChave'),antes=campo.value,caret=campo.selectionStart;
 const pos=antes.slice(0,caret).replace(/\D/g,'').length;
 const digits=antes.replace(/\D/g,'').slice(0,44);
 campo.value=(digits.match(/.{1,4}/g)||[]).join(' ');
 const cursor=pos+Math.floor(Math.max(0,pos-1)/4);campo.setSelectionRange(cursor,cursor);
 $('notaEnviarChave').disabled=!chaveValida(digits);
 $('notaChaveAjuda').textContent=digits.length===44&&!chaveValida(digits)?'Chave inválida. Confira os números e o dígito final.':digits.length+' de 44 dígitos';
});
let enviando = false;
function feedback(titulo,mensagem,processando,url) {
    $('notaFeedbackTitulo').textContent=titulo;
    $('notaFeedbackMensagem').textContent=mensagem;
    $('notaFeedbackFechar').hidden=processando;
    $('notaFeedbackPortal').hidden=true;
    if(url) {
        try { const u=new URL(url); if(u.protocol==='https:' && u.hostname==='www.nfce.fazenda.sp.gov.br'){
            $('notaFeedbackPortal').href=u.href;$('notaFeedbackPortal').hidden=false;
        }} catch(_) {}
    }
    if(!$('notaFeedbackDialog').open) $('notaFeedbackDialog').showModal();
}
$('notaFeedbackFechar').addEventListener('click',()=>$('notaFeedbackDialog').close());
$('notaChaveForm').addEventListener('submit',async e=>{
 e.preventDefault();const digits=$('notaChave').value.replace(/\D/g,'');
 if(!chaveValida(digits)||enviando)return;
 enviando=true;$('notaEnviarChave').disabled=true;$('notaEnviarChave').textContent='Consultando…';
 feedback('Consultando nota fiscal','Estamos buscando os dados no portal fiscal. Aguarde…',true);
 try {
    if(!window.VisionNotas?.consultarChave) throw new Error('O leitor não carregou corretamente. Atualize a página e tente novamente.');
    const dados=await window.VisionNotas.consultarChave(digits);
    if(dados.itens?.length) feedback('Nota localizada',dados.itens.length+' itens encontrados. Confira mercado, filial, data e valores e clique em “Salvar produtos e preços pagos”. Só após essa confirmação ela aparecerá em Minhas notas.',false);
    else feedback('Consulta precisa de atenção',dados.aviso || 'Não foi possível obter os itens automaticamente. O portal pode exigir CAPTCHA. Abra a consulta oficial ou importe a foto/XML. A nota ainda não foi salva.',false,dados.urlConsulta);
 } catch(err) { feedback('Não foi possível consultar',err.message,false); }
 finally { enviando=false;$('notaEnviarChave').disabled=!chaveValida($('notaChave').value.replace(/\D/g,''));$('notaEnviarChave').textContent='Enviar nota fiscal';}
});
document.addEventListener('vision:nota-salva',()=>feedback('Nota salva','A importação foi concluída. O cupom está disponível em Minhas notas.',false));
document.addEventListener('vision:nota-lida',()=>tela(null));
document.addEventListener('vision:etapa',e=>{if(e.detail!=='leitorqr')tela(null);});
})();
