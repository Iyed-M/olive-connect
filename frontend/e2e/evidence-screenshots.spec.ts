import { expect, test, type Page } from '@playwright/test'
import path from 'node:path'
import fs from 'node:fs'

type EvidenceAccount = {
  role: string
  email?: string
  password?: string
  routes: string[]
}

const publicRoutes = ['/', '/login', '/register']

const accounts: EvidenceAccount[] = [
  {
    role: 'ADMIN',
    email: process.env.EVIDENCE_ADMIN_EMAIL,
    password: process.env.EVIDENCE_ADMIN_PASSWORD,
    routes: ['/admin/users', '/admin/registrations', '/dashboard', '/logistics/workers', '/logistics/equipment', '/logistics/calendar', '/logistics/supplies', '/intranet/orchards', '/intranet/rounds', '/intranet/imports', '/intranet/settings', '/notifications'],
  },
  {
    role: 'COLLECTION_MANAGER',
    email: process.env.EVIDENCE_COLLECTION_MANAGER_EMAIL,
    password: process.env.EVIDENCE_COLLECTION_MANAGER_PASSWORD,
    routes: ['/dashboard', '/collection/requests', '/collection/planning', '/logistics/calendar', '/intranet/orchards', '/intranet/rounds', '/collection/alerts', '/notifications'],
  },
  {
    role: 'TEAM_LEADER',
    email: process.env.EVIDENCE_TEAM_LEADER_EMAIL,
    password: process.env.EVIDENCE_TEAM_LEADER_PASSWORD,
    routes: ['/rounds/mine', '/notifications'],
  },
  {
    role: 'LOGISTICS_MANAGER',
    email: process.env.EVIDENCE_LOGISTICS_MANAGER_EMAIL,
    password: process.env.EVIDENCE_LOGISTICS_MANAGER_PASSWORD,
    routes: ['/logistics/workers', '/logistics/equipment', '/logistics/calendar', '/logistics/supplies', '/intranet/orchards', '/notifications'],
  },
  {
    role: 'MILL_MANAGER',
    email: process.env.EVIDENCE_MILL_MANAGER_EMAIL,
    password: process.env.EVIDENCE_MILL_MANAGER_PASSWORD,
    routes: ['/dashboard', '/mill/incoming', '/mill/capacity', '/notifications'],
  },
  {
    role: 'FARMER',
    email: process.env.EVIDENCE_FARMER_EMAIL,
    password: process.env.EVIDENCE_FARMER_PASSWORD,
    routes: ['/farmer', '/farmer/requests', '/farmer/orchards', '/farmer/alerts/new', '/farmer/receptions', '/notifications'],
  },
]

const screenshotRoot = path.resolve(process.env.EVIDENCE_SCREENSHOT_DIR ?? '../report/evidence/screenshots')

function filePart(value: string) {
  return value.replace(/^\//, '').replaceAll('/', '__').replaceAll(/[^a-z0-9_-]/gi, '-') || 'home'
}

async function capture(page: Page, project: string, role: string, route: string) {
  const directory = path.join(screenshotRoot, project, role.toLowerCase())
  fs.mkdirSync(directory, { recursive: true })
  await page.goto(route, { waitUntil: 'domcontentloaded' })
  await page.screenshot({ path: path.join(directory, `${filePart(route)}.png`), fullPage: true })
}

async function login(page: Page, account: EvidenceAccount) {
  await page.goto('/login', { waitUntil: 'domcontentloaded' })
  await page.getByLabel('Email').fill(account.email!)
  await page.getByLabel('Mot de passe').fill(account.password!)
  const response = page.waitForResponse((candidate) => {
    const url = new URL(candidate.url())
    return url.pathname === '/api/auth/login' && candidate.request().method() === 'POST'
  })
  await page.getByRole('button', { name: 'Se connecter' }).click()
  expect((await response).ok()).toBe(true)
}

test('public pages — screenshots', async ({ page }, testInfo) => {
  for (const route of publicRoutes) await capture(page, testInfo.project.name, 'visitor', route)
})

for (const account of accounts) {
  test(`${account.role} pages — screenshots`, async ({ page }, testInfo) => {
    test.skip(!account.email || !account.password, `Set the EVIDENCE_${account.role}_EMAIL and password variables`)
    await login(page, account)
    for (const route of account.routes) await capture(page, testInfo.project.name, account.role, route)
  })
}
