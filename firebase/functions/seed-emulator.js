'use strict';
// Fixed demo-only environment: never accepts a production project or remote host.
process.env.FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099';
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.FIREBASE_STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
const { initializeApp } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');
const { getStorage } = require('firebase-admin/storage');
initializeApp({ projectId: 'demo-em-cleaning', storageBucket: 'demo-em-cleaning.appspot.com' });
(async () => {
  for (const name of ['alice', 'bob', 'admin']) {
    let user;
    try { user = await getAuth().getUser(name); }
    catch (error) {
      if (error.code !== 'auth/user-not-found') throw error;
      user = await getAuth().createUser({ uid: name, email: `${name}@example.test`, password: 'Demo-Passwort!2026' });
    }
    const admin = name === 'admin';
    await getAuth().setCustomUserClaims(user.uid, admin ? { admin: true } : { employeeId: name });
    await getFirestore().doc(`pushRecipients/${name}`).set({ admin, employeeId: admin ? '' : name });
    if (admin) continue;
    await getFirestore().doc(`employees/${name}`).set({ id: name, name: `Test ${name}`, telefon: '', art: 'Test', lohn: '0' });
    for (const kind of ['Arbeitsvertrag', 'Lohnabrechnung']) {
      const id = `${name}-${kind}`;
      const storagePath = `documents/employees/${name}/${id}.txt`;
      await getStorage().bucket().file(storagePath).save(Buffer.from(`REINE TESTDATEI: ${kind} fuer ${name}. Keine Mitarbeiterdaten.`), { contentType: 'text/plain', metadata: { metadata: { employeeId: name } } });
      await getFirestore().doc(`companyDocuments/${id}`).set({ id, employeeId: name, employeeName: `Test ${name}`, title: `TEST ${kind}`, type: kind, storagePath, fileName: `${id}.txt`, mimeType: 'text/plain', url: '' });
    }
  }
  console.log('Demo accounts ready: alice@example.test, bob@example.test, admin@example.test. Password: Demo-Passwort!2026');
})().catch(error => { console.error(error.message); process.exitCode = 1; });
