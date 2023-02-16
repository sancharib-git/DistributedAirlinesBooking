import { h, render } from "https://esm.sh/preact@10.11.2";
import Router from "https://esm.sh/preact-router@4.1.0";
import htm from "https://esm.sh/htm@3.1.1";
import { initializeApp } from "https://www.gstatic.com/firebasejs/9.9.4/firebase-app.js";
import {
  getAuth,
  connectAuthEmulator,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/9.9.4/firebase-auth.js";
import { Header } from "./header.js";
import { Flights } from "./flights.js";
import { setAuth, setIsManager } from "./state.js";
import { FlightTimes } from "./flight_times.js";
import { FlightSeats } from "./flight_seats.js";
import { Cart } from "./cart.js";
import { Account } from "./account.js";
import { Manager } from "./manager.js";
import { Login } from "./login.js";
import { getFirestore, collection, getDocs } from "https://www.gstatic.com/firebasejs/9.9.4/firebase-firestore.js"

const html = htm.bind(h);

let firebaseConfig;
if (location.hostname === "localhost") {
  firebaseConfig = {
    apiKey: "AIzaSyBoLKKR7OFL2ICE15Lc1-8czPtnbej0jWY",
    projectId: "demo-distributed-systems-kul",
  };
} else {
  firebaseConfig = {
    // TODO: for level 2, paste your config here
    // TODO: for level 2, paste your config here
          // Import the functions you need from the SDKs you need

          // TODO: Add SDKs for Firebase products that you want to use
          // https://firebase.google.com/docs/web/setup#available-libraries

          // Your web app's Firebase configuration
          // For Firebase JS SDK v7.20.0 and later, measurementId is optional
            apiKey: "AIzaSyDnsTjUg6Jjd7dlAo3lyWKdrBw_a9Y31qc",
            authDomain: "airlines-booking-f35af.firebaseapp.com",
            projectId: "airlines-booking-f35af",
            storageBucket: "airlines-booking-f35af.appspot.com",
            messagingSenderId: "128457865533",
            appId: "1:128457865533:web:7fb7192905d95a23a7ed54",
            measurementId: "G-NMYY45F5XB"


          // Initialize Firebase


          // Get a list of cities from your database
          /*async function getCities(db) {
            const citiesCol = collection(db, 'cities');
            const citySnapshot = await getDocs(citiesCol);
            const cityList = citySnapshot.docs.map(doc => doc.data());
            return cityList;
          }*/
  };
}

const firebaseApp = initializeApp(firebaseConfig);
const auth = getAuth(firebaseApp);
const db = getFirestore(firebaseApp);
setAuth(auth);
if (location.hostname === "localhost") {
  connectAuthEmulator(auth, "http://localhost:8082", { disableWarnings: true });
}
let rendered = false;
onAuthStateChanged(auth, (user) => {
  if (user == null) {
    if (location.pathname !== "/login") {
      location.assign("/login");
    }
  } else {
    auth.currentUser.getIdTokenResult().then((idTokenResult) => {
      setIsManager(idTokenResult.claims.role === "manager");
    });
  }

  if (!rendered) {
    if (location.pathname === "/login") {
      render(html` <${Login} />`, document.body);
    } else {
      render(
        html`
            <${Header}/>
            <${Router}>
                <${Flights} path="/"/>
                <${FlightTimes} path="/flights/:airline/:flightId"/>
                <${FlightSeats} path="/flights/:airline/:flightId/:time"/>
                <${Cart} path="/cart"/>
                <${Account} path="/account"/>
                <${Manager} path="/manager"/>
            </${Router}>
        `,
        document.body
      );
    }
    rendered = true;
  }
});
