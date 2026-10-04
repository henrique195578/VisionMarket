const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
test('formata chave, valida dígito e mostra retorno da consulta',async()=>{
 const elements={},events={},dispatch=[];
 const get=id=>elements[id]||(elements[id]={hidden:false,value:'',selectionStart:0,dataset:{},disabled:false,open:false,showModal(){this.open=true},close(){this.open=false},listeners:{},addEventListener(k,f){this.listeners[k]=f},click(){this.clicks=(this.clicks||0)+1},focus(){},setSelectionRange(a){this.selectionStart=a}});
 const document={getElementById:get,querySelectorAll:()=>[],querySelector:()=>get('lista'),body:{classList:{toggle(){}}},addEventListener:(k,f)=>events[k]=f,dispatchEvent:e=>dispatch.push(e)};
 vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/nota-captura.js','utf8'),{document,window:{VisionNotas:{consultarChave:async chave=>{dispatch.push({detail:chave});return {itens:[{}]}}}},URL,CustomEvent:class{constructor(type,o){this.type=type;this.detail=o.detail}}});
 get('notaDigitarManual').listeners.click();assert.equal(get('leitorqr').dataset.captura,'manual');assert.ok(get('notaPararCamera').clicks);
 const key='35261006057223030755650110000447971110028220';
 get('notaChave').value=key;get('notaChave').selectionStart=44;get('notaChave').listeners.input();
 assert.equal(get('notaChave').value,key.match(/.{4}/g).join(' '));assert.equal(get('notaEnviarChave').disabled,false);
 await get('notaChaveForm').listeners.submit({preventDefault(){}});assert.equal(dispatch[0].detail,key);assert.equal(get('notaFeedbackTitulo').textContent,'Nota localizada');assert.equal(get('notaFeedbackDialog').open,true);
 get('notaChave').value=key.slice(0,-1)+'1';get('notaChave').listeners.input();assert.equal(get('notaEnviarChave').disabled,true);
});
