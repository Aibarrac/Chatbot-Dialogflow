
'use strict';

const functions = require('firebase-functions');
const admin = require('firebase-admin');
const {WebhookClient} = require('dialogflow-fulfillment');
const {Card, Suggestion} = require('dialogflow-fulfillment');

// --Inicializo la base de datos a usar--
admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  databaseURL: 'ws://satisfaction-ce35c.firebaseio.com/'
});

process.env.DEBUG = 'dialogflow:debug'; // enables lib debugging statements
 
exports.dialogflowFirebaseFulfillment = functions.https.onRequest((request, response) => {
  const agent = new WebhookClient({ request, response });
  console.log('Dialogflow Request headers: ' + JSON.stringify(request.headers));
  console.log('Dialogflow Request body: ' + JSON.stringify(request.body));
 
  // --Funciones para los diferentes intent--
  
  	function HandleReadCitaFromDB(agent){ //Leo los datos de firebase
      const date = agent.parameters.date;
      const fecha_slice = date.slice(0,10);
      
      return admin.database().ref('CITAS/' + fecha_slice).once('value')
        .then((snapshot) =>{
        	let output = "";
        	snapshot.forEach((childSnapshot) =>{
               	const lugar = childSnapshot.child('lugar').val();
               	const fecha = childSnapshot.child('fecha').val();
               	const hora = childSnapshot.child('hora').val();
              
              	output += `Lugar: ${lugar}\nFecha: ${fecha}\nHora: ${hora}\n******\n`;
              
            });
        /* Solo se puede poner un agent.add por método ya que si no
           no es capaz de mostrarlo correctamente en Android.*/
        agent.add(output);
      });
    }
  	function HandleSaveCitaToDB(agent){ // Añado una nueva cita a firebase
      const time = agent.parameters.time;
      const date = agent.parameters.date;
      const lugar = agent.parameters.address;
      // --Filtro los valores de la cadena--
      let fecha = date.slice(0,10);
      let hora = time.slice(11,19);
      
      //Comprueba si el valor está repetido
      const adm = admin.database();
      const rf = adm.ref('CITAS/' + fecha);
      var repetido = false;
      return rf.once('value').then((snapshot) =>{
        	snapshot.forEach((childSnapshot) => {
              	if((childSnapshot.child('hora').val()) === hora){
                  	repetido = true;
                }
            });
        	if(repetido === true){
        		agent.add('Esa fecha ya está reservada');
      		}
        	if(repetido === false){
              	saveCita(fecha, hora, lugar);
           }
      	}); 
    }
    function saveCita(fecha, hora, lugar){
      agent.add('Cita guardada satisfactoriamente!');
      return admin.database().ref('CITAS/' + fecha + '/' + hora).set({
        fecha: fecha,
        hora: hora,
        lugar: lugar
      });
    }
  
  	function HandleSaveCumple(agent){
      let date = agent.parameters.date;
      let nombre = agent.parameters.name;
      if(date === ""){
        agent.add('Ha ocurrido un error');
      }else{
        agent.add('Cumpleaños guardado satisfactoriamente!');
        return admin.database().ref('Cumpleaños/' + nombre).set({
          fecha : date
        });
      }
    }
  	
  	function HandleReadCumple(agent){
      const name = agent.parameters.name;
      
      return admin.database().ref('Cumpleaños/' + name[0]).once('value')
        .then((snapshot) =>{
			const date = snapshot.child('fecha').val();
        	let fecha = date.slice(5,10);
        	let mes = fecha.slice(0,2);
        	let dia = fecha.slice(3,6);
        
        	if(name[0] === 'Usuario'){
            	agent.add(`Tu cumpleños es el ${dia}-${mes}`);
            }else{
              	agent.add(`El cumpleños de ${name[0]} es el ${fecha}`);
            }
        	
      });
    }
  
  function HandleOpenApp(agent){
    const app = (agent.parameters.app).toLowerCase();
    let output = 'Esa app no está disponible';
    
    switch (app) {
      case 'instagram':
        output = 'com.instagram.android';
        break;
      case 'youtube':
        output = 'com.google.android.youtube';
        break;
      case 'chrome':
        output = 'com.android.chrome';
        break;
      case 'spotify':
        output = 'com.spotify.music';
        break;
    }
    return agent.add(output);
    
  }
  
  function HandleJoke(agent){
    
    var getRandom = Math.floor(Math.random() * (10 - 1) ) + 1;
    const getID = getRandom.toString();
    
    return admin.database().ref('Chistes/').once('value')
    	.then((snapshot) =>{
      		
      		agent.add(snapshot.child(getID).val());
    });
  }
  // --Define para cada intent un método--

  let intentMap = new Map();
  intentMap.set('SaveCitaToDB', HandleSaveCitaToDB);
  intentMap.set('ReadCitaFromDB', HandleReadCitaFromDB);
  intentMap.set('SaveCumple', HandleSaveCumple);
  intentMap.set('ReadCumple', HandleReadCumple);
  intentMap.set('openApp', HandleOpenApp);
  intentMap.set('smalltalk.user.bored', HandleJoke);
  agent.handleRequest(intentMap);
});
