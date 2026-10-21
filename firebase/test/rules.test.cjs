const { before, after, test } = require('node:test');
const { readFileSync } = require('node:fs');
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { doc, getDoc, getDocs, collection, query, where, setDoc, updateDoc, serverTimestamp } = require('firebase/firestore');
const { ref, uploadBytes, getBytes } = require('firebase/storage');
let env;
const context = (uid, claims = {}) => env.authenticatedContext(uid, { firebase: { sign_in_provider: 'password' }, ...claims });
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-em-cleaning',
    firestore: { rules: readFileSync('firebase/firestore.rules', 'utf8') },
    storage: { rules: readFileSync('firebase/storage.rules', 'utf8') } });
  await env.withSecurityRulesDisabled(async ctx => {
    for (const employeeId of ['alice', 'bob']) {
      await setDoc(doc(ctx.firestore(), 'employees', employeeId), { name: employeeId });
      await setDoc(doc(ctx.firestore(), 'companyDocuments', employeeId), { employeeId, storagePath: `documents/employees/${employeeId}/payroll.pdf`, url: '' });
      await uploadBytes(ref(ctx.storage(), `documents/employees/${employeeId}/payroll.pdf`), new Uint8Array([1, 2, 3]), { customMetadata: { employeeId } });
      await setDoc(doc(ctx.firestore(), 'tickets', employeeId), { employeeId, status: 'Offen' });
      await setDoc(doc(ctx.firestore(), 'leaveRequests', employeeId), { employeeId, status: 'Offen' });
      await setDoc(doc(ctx.firestore(), 'timesheets', employeeId), { mitarbeiterId: employeeId });
      await setDoc(doc(ctx.firestore(), 'messages', employeeId), { employeeId, sender: 'admin', message: 'test' });
    }
    await setDoc(doc(ctx.firestore(), 'companyDocuments', 'unassigned'), { employeeId: '' });
  });
});
after(async () => { if (env) await env.cleanup(); });
test('employee reads own documents and profile, never another employee or unassigned documents', async () => {
  const db = context('a', { employeeId: 'alice' }).firestore();
  for (const kind of ['employees', 'companyDocuments', 'tickets', 'leaveRequests', 'timesheets', 'messages']) {
    await assertSucceeds(getDoc(doc(db, kind, 'alice')));
    await assertFails(getDoc(doc(db, kind, 'bob')));
  }
  await assertFails(getDoc(doc(db, 'companyDocuments', 'unassigned')));
  await assertFails(getDocs(collection(db, 'employees')));
});
test('owner-constrained document query succeeds; unfiltered query fails', async () => {
  const db = context('a', { employeeId: 'alice' }).firestore();
  await assertSucceeds(getDocs(query(collection(db, 'companyDocuments'), where('employeeId', '==', 'alice'))));
  await assertFails(getDocs(collection(db, 'companyDocuments')));
  await assertFails(getDocs(query(collection(db, 'companyDocuments'), where('employeeId', '==', 'bob'))));
});
test('anonymous, signed out and unprovisioned accounts cannot read documents', async () => {
  for (const ctx of [env.unauthenticatedContext(), env.authenticatedContext('anon', { firebase: { sign_in_provider: 'anonymous' }, employeeId: 'alice', admin: true }), context('unassigned')]) {
    await assertFails(getDoc(doc(ctx.firestore(), 'companyDocuments', 'alice')));
    await assertFails(getBytes(ref(ctx.storage(), 'documents/employees/alice/payroll.pdf')));
  }
});
test('storage enforces ownership, denies employee uploads and unknown paths', async () => {
  const storage = context('a', { employeeId: 'alice' }).storage();
  await assertSucceeds(getBytes(ref(storage, 'documents/employees/alice/payroll.pdf')));
  await assertFails(getBytes(ref(storage, 'documents/employees/bob/payroll.pdf')));
  await assertFails(getBytes(ref(storage, 'documents/general/payroll.pdf')));
  await assertFails(uploadBytes(ref(storage, 'documents/employees/alice/new.pdf'), new Uint8Array([1])));
});
test('employee cannot self-promote, forge messages, reassign documents or approve requests', async () => {
  const db = context('a', { employeeId: 'alice' }).firestore();
  await assertFails(setDoc(doc(db, 'pushRecipients', 'a'), { admin: true }));
  await assertFails(updateDoc(doc(db, 'employees', 'alice'), { admin: true }));
  await assertFails(updateDoc(doc(db, 'companyDocuments', 'alice'), { employeeId: 'bob' }));
  await assertFails(updateDoc(doc(db, 'tickets', 'alice'), { status: 'Erledigt' }));
  await assertFails(updateDoc(doc(db, 'leaveRequests', 'alice'), { status: 'Genehmigt' }));
  await assertFails(setDoc(doc(db, 'messages', 'spoof'), { employeeId: 'alice', sender: 'admin', message: 'test', createdAt: serverTimestamp() }));
  await assertSucceeds(setDoc(doc(db, 'messages', 'valid'), { employeeId: 'alice', sender: 'employee', message: 'test', createdAt: serverTimestamp() }));
  await assertFails(setDoc(doc(db, 'messages', 'foreign'), { employeeId: 'bob', sender: 'employee', message: 'test', createdAt: serverTimestamp() }));
});
test('employees can submit own leave and tickets but not for another employee', async () => {
  const db = context('a', { employeeId: 'alice' }).firestore();
  for (const kind of ['tickets', 'leaveRequests']) {
    const fields = kind === 'tickets' ? { subject: 'test', description: '', type: 'test' } : { from: '2026-11-01', to: '2026-11-02', note: '' };
    await assertSucceeds(setDoc(doc(db, kind, 'new'), { ...fields, employeeId: 'alice', status: 'Offen', createdAt: serverTimestamp() }));
    await assertFails(setDoc(doc(db, kind, 'forged'), { ...fields, employeeId: 'bob', status: 'Offen', createdAt: serverTimestamp() }));
  }
});
test('admin can query and upload assigned files; metadata/path mismatch denied', async () => {
  const ctx = context('admin', { admin: true });
  await assertSucceeds(getDocs(collection(ctx.firestore(), 'companyDocuments')));
  await assertSucceeds(uploadBytes(ref(ctx.storage(), 'documents/employees/alice/new.pdf'), new Uint8Array([1]), { customMetadata: { employeeId: 'alice' } }));
  await assertFails(uploadBytes(ref(ctx.storage(), 'documents/employees/bob/new.pdf'), new Uint8Array([1]), { customMetadata: { employeeId: 'alice' } }));
  await assertSucceeds(setDoc(doc(ctx.firestore(), 'companyDocuments', 'new'), { employeeId: 'alice', storagePath: 'documents/employees/alice/new.pdf', url: '' }));
  await assertFails(setDoc(doc(ctx.firestore(), 'companyDocuments', 'bad'), { employeeId: 'bob', storagePath: 'documents/employees/alice/new.pdf' }));
});
test('device registration is restricted to authenticated owner, recipient mappings remain server-only', async () => {
  const db = context('a', { employeeId: 'alice' }).firestore();
  await assertSucceeds(setDoc(doc(db, 'pushRecipients/a/devices/test-token'), { token: 'test-token', platform: 'android' }));
  await assertFails(setDoc(doc(db, 'pushRecipients/b/devices/test-token'), { token: 'test-token', platform: 'android' }));
  await assertFails(setDoc(doc(db, 'pushRecipients/a/devices/other'), { token: 'test-token', platform: 'android', admin: true }));
});
