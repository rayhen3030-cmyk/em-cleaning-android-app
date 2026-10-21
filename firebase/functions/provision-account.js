#!/usr/bin/env node
'use strict';
// Explicit account provisioning only. Does not create or modify employee records.
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getAuth } = require('firebase-admin/auth');
const { getFirestore } = require('firebase-admin/firestore');
const args = process.argv.slice(2);
const value = key => args[args.indexOf(key) + 1];
const projectId = args.includes('--project') ? value('--project') : '';
const uid = args.includes('--uid') ? value('--uid') : '';
const employeeId = args.includes('--employee') ? value('--employee') : '';
const admin = args.includes('--admin');
if (!projectId || !uid || (admin === !!employeeId) || (employeeId && !/^[A-Za-z0-9_-]+$/.test(employeeId))) {
  console.error('Usage: node provision-account.js --project PROJECT --uid AUTH_UID (--employee EMPLOYEE_DOCUMENT_ID | --admin) [--apply]');
  process.exit(1);
}
if (process.env.FIREBASE_AUTH_EMULATOR_HOST && !projectId.startsWith('demo-')) throw new Error('Emulator requires demo project');
if (process.env.FIRESTORE_EMULATOR_HOST && !process.env.FIREBASE_AUTH_EMULATOR_HOST) throw new Error('Both emulators must be configured');
if (!args.includes('--apply')) {
  console.log(JSON.stringify({ dryRun: true, projectId, uid, role: admin ? 'admin' : 'employee', employeeId: employeeId || null }));
  process.exit(0);
}
initializeApp({ credential: applicationDefault(), projectId });
(async () => {
  const auth = getAuth();
  const db = getFirestore();
  const account = await auth.getUser(uid);
  if (account.disabled || !account.email || !account.providerData.some(p => p.providerId === 'password')) throw new Error('An enabled email/password account is required');
  if (!admin && !(await db.doc(`employees/${employeeId}`).get()).exists) throw new Error('Employee document does not exist');
  if (!admin) {
    const duplicates = await db.collection('pushRecipients').where('employeeId', '==', employeeId).get();
    if (duplicates.docs.some(doc => doc.id !== uid)) throw new Error('Employee already mapped; review existing mapping before reassignment');
  }
  const claims = { ...(account.customClaims || {}) };
  delete claims.admin;
  delete claims.employeeId;
  if (admin) claims.admin = true; else claims.employeeId = employeeId;
  await auth.setCustomUserClaims(uid, claims);
  await auth.revokeRefreshTokens(uid);
  await db.doc(`pushRecipients/${uid}`).set({ admin, employeeId: employeeId || '' });
  console.log('Account mapping applied. Sign in again to obtain new claims. Employee data unchanged.');
})().catch(error => { console.error(error.message); process.exitCode = 1; });
