const { before, after, test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const { initializeApp, deleteApp } = require('firebase/app');
const { getAuth, connectAuthEmulator, signInWithEmailAndPassword, getIdTokenResult, signOut } = require('firebase/auth');
const { getFirestore, connectFirestoreEmulator, collection, query, where, getDocs, getDoc, doc } = require('firebase/firestore');
const { getStorage, connectStorageEmulator, getBytes, ref } = require('firebase/storage');
let app, auth, db, storage;
before(() => {
  // This fixture script hard-codes demo project + localhost, with no production credentials.
  execFileSync(process.execPath, ['firebase/functions/seed-emulator.js'], { stdio: 'pipe' });
  app = initializeApp({ apiKey: 'demo-em-cleaning-emulator-only-key', projectId: 'demo-em-cleaning', storageBucket: 'demo-em-cleaning.appspot.com', appId: '1:1234567890:android:0000000000000000' }, 'account-flow');
  auth = getAuth(app); connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  db = getFirestore(app); connectFirestoreEmulator(db, '127.0.0.1', 8080);
  storage = getStorage(app); connectStorageEmulator(storage, '127.0.0.1', 9199);
});
after(async () => { if (app) await deleteApp(app); });
test('real email/password sign-in obtains employee claim, loads own contract/payroll and denies foreign files', async () => {
  for (const name of ['alice', 'bob']) {
    const result = await signInWithEmailAndPassword(auth, `${name}@example.test`, 'Demo-Passwort!2026');
    const token = await getIdTokenResult(result.user, true);
    assert.equal(token.claims.employeeId, name);
    assert.equal(token.claims.admin, undefined);
    const docs = await getDocs(query(collection(db, 'companyDocuments'), where('employeeId', '==', token.claims.employeeId)));
    assert(docs.docs.every(doc => doc.data().employeeId === name));
    for (const kind of ['Arbeitsvertrag', 'Lohnabrechnung']) {
      const document = docs.docs.find(doc => doc.id === `${name}-${kind}`);
      assert(document);
      const bytes = await getBytes(ref(storage, document.data().storagePath));
      assert(new TextDecoder().decode(bytes).includes(`fuer ${name}`));
    }
    const other = name === 'alice' ? 'bob' : 'alice';
    await assert.rejects(getDoc(doc(db, 'companyDocuments', `${other}-Arbeitsvertrag`)), error => error.code === 'permission-denied');
    await assert.rejects(getBytes(ref(storage, `documents/employees/${other}/${other}-Arbeitsvertrag.txt`)), error => error.code === 'storage/unauthorized');
    await signOut(auth);
    await assert.rejects(getDocs(query(collection(db, 'companyDocuments'), where('employeeId', '==', name))), error => error.code === 'permission-denied');
  }
});
test('wrong password fails; provisioned admin gets role and full query', async () => {
  await assert.rejects(signInWithEmailAndPassword(auth, 'alice@example.test', 'wrong-password'));
  const result = await signInWithEmailAndPassword(auth, 'admin@example.test', 'Demo-Passwort!2026');
  assert.equal((await getIdTokenResult(result.user, true)).claims.admin, true);
  assert((await getDocs(collection(db, 'companyDocuments'))).size >= 4);
  await signOut(auth);
});
