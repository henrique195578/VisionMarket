(function(){
'use strict';
const $=id=>document.getElementById(id), leitor=$('leitorqr');
function tela(modo){
    $('notaPararCamera').click();
    if(modo) leitor.dataset.captura=modo; else delete leitor.dataset.captura;
    document.body.classList.toggle('nota-capturando',!!modo);
    $('notaManualArea').hidden=modo!=='manual';
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
$('notaChaveForm').addEventListener('submit',e=>{
 e.preventDefault();const digits=$('notaChave').value.replace(/\D/g,'');
 if(!chaveValida(digits))return;
 document.dispatchEvent(new CustomEvent('vision:nota-chave',{detail:digits}));
});
document.addEventListener('vision:nota-lida',()=>tela(null));
document.addEventListener('vision:etapa',e=>{if(e.detail!=='leitorqr')tela(null);});
})();
