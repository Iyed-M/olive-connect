import { expect, test, type Page, type TestInfo } from '@playwright/test'

const adminEmail = process.env.E2E_ADMIN_EMAIL ?? process.env.ADMIN_EMAIL ?? 'admin@oliveconnect.tn'
const adminPassword = process.env.E2E_ADMIN_PASSWORD ?? process.env.ADMIN_PASSWORD ?? 'admin1234'

function apiResponse(response: { url: () => string; request: () => { method: () => string } }, path: string, method: string) {
  const url = new URL(response.url())
  return url.pathname === path && response.request().method() === method
}

function applicantFor(testInfo: TestInfo) {
  const project = testInfo.project.name.replace(/[^a-z0-9]/gi, '').toLowerCase()
  const projectCode = testInfo.project.name === 'mobile' ? '1' : '2'
  const suffix = `${Date.now()}-${project}-${testInfo.workerIndex}-${testInfo.retry}`
  const digits = `${Date.now()}${testInfo.workerIndex}${testInfo.retry}${projectCode}`.slice(-8).padStart(8, '0')
  return {
    firstName: 'E2E',
    lastName: `Visitor ${testInfo.project.name}`,
    email: `e2e-${suffix}@example.test`,
    phone: '+21622123456',
    cin: digits,
    message: 'Release journey registration',
    memberNumber: `E2E-${suffix}`,
  }
}

async function dismissGuidedTour(page: Page) {
  const tour = page.getByTestId('guided-tour')
  try {
    await tour.waitFor({ state: 'visible', timeout: 1_500 })
    await tour.getByRole('button', { name: 'Skip tour' }).click()
    await expect(tour).toBeHidden()
  } catch {
    // Existing compose data may already have onboardingDone=true for this account.
  }
}

async function login(page: Page, email: string, password: string) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(password)
  const response = page.waitForResponse((candidate) => apiResponse(candidate, '/api/auth/login', 'POST'))
  await page.getByRole('button', { name: 'Log in' }).click()
  expect((await response).ok()).toBe(true)
}

async function logout(page: Page) {
  await dismissGuidedTour(page)
  await page.getByRole('button', { name: 'Open profile menu' }).click()
  await page.getByRole('menuitem', { name: 'Log out' }).click()
  await expect(page).toHaveURL(/\/login$/)
}

test('visitor can open the public map', async ({ page }) => {
  const mapResponse = page.waitForResponse((response) => apiResponse(response, '/api/map/public', 'GET'))
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'OliveConnect' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Join the cooperative' })).toBeVisible()
  await expect(page.getByRole('form', { name: 'Map filters' })).toBeVisible()
  await expect(page.getByLabel('Variety')).toBeVisible()
  expect((await mapResponse).ok()).toBe(true)
})

test('visitor can submit a registration request', async ({ page }, testInfo) => {
  const applicant = applicantFor(testInfo)
  await page.goto('/register')

  await page.getByLabel('First name').fill(applicant.firstName)
  await page.getByLabel('Last name').fill(applicant.lastName)
  await page.getByLabel('Email').fill(applicant.email)
  await page.getByLabel('Phone').fill(applicant.phone)
  await page.getByLabel('CIN').fill(applicant.cin)
  await page.getByLabel('Message (optional)').fill(applicant.message)

  const response = page.waitForResponse((candidate) => apiResponse(candidate, '/api/registrations', 'POST'))
  await page.getByRole('button', { name: 'Send request' }).click()
  expect((await response).status()).toBe(201)
  await expect(page.getByRole('heading', { name: 'Request sent' })).toBeVisible()
  await expect(page.getByText('An administrator of the cooperative will review your request.')).toBeVisible()
})

test('admin approval leads to a farmer role journey', async ({ page }, testInfo) => {
  const applicant = applicantFor(testInfo)

  await page.goto('/register')
  await page.getByLabel('First name').fill(applicant.firstName)
  await page.getByLabel('Last name').fill(applicant.lastName)
  await page.getByLabel('Email').fill(applicant.email)
  await page.getByLabel('Phone').fill(applicant.phone)
  await page.getByLabel('CIN').fill(applicant.cin)
  await page.getByLabel('Message (optional)').fill(applicant.message)
  const registration = page.waitForResponse((candidate) => apiResponse(candidate, '/api/registrations', 'POST'))
  await page.getByRole('button', { name: 'Send request' }).click()
  expect((await registration).status()).toBe(201)

  await login(page, adminEmail, adminPassword)
  await expect(page).toHaveURL(/\/admin\/users\/?$/)
  await dismissGuidedTour(page)

  await page.goto('/admin/registrations')
  const request = page.locator('li').filter({ hasText: applicant.email })
  await expect(request).toBeVisible()
  await request.getByLabel('Member number').fill(applicant.memberNumber)
  await request.getByLabel('Delegation').selectOption('SFAX_VILLE')
  const approval = page.waitForResponse((candidate) => candidate.url().includes('/api/registrations/') && candidate.url().endsWith('/approve') && candidate.request().method() === 'POST')
  await request.getByRole('button', { name: 'Approve' }).click()
  expect((await approval).ok()).toBe(true)

  const approvalStatus = page.getByRole('status')
  await expect(approvalStatus).toContainText(applicant.email)
  const temporaryPassword = (await approvalStatus.locator('p').nth(1).innerText()).trim()
  expect(temporaryPassword).toMatch(/^\S{12}$/)
  await approvalStatus.getByRole('button', { name: 'Done' }).click()

  await logout(page)
  await login(page, applicant.email, temporaryPassword)
  await expect(page).toHaveURL(/\/change-password\/?$/)
  await expect(page.getByRole('heading', { name: 'Change your password' })).toBeVisible()

  const newPassword = 'E2eFarmerPassword!2026'
  await page.getByLabel('Current password', { exact: true }).fill(temporaryPassword)
  await page.getByLabel('New password', { exact: true }).fill(newPassword)
  await page.getByLabel('Confirm new password', { exact: true }).fill(newPassword)
  const passwordChange = page.waitForResponse((candidate) => apiResponse(candidate, '/api/me/password', 'PATCH'))
  await page.getByRole('button', { name: 'Change password' }).click()
  expect((await passwordChange).status()).toBe(204)
  await expect(page).toHaveURL(/\/farmer\/?$/)
  await expect(page.getByRole('heading', { name: 'Farmer home' })).toBeVisible()

  await dismissGuidedTour(page)
  await page.getByRole('button', { name: 'Open contextual help' }).click()
  const helpDialog = page.getByRole('dialog', { name: 'Farmer home' })
  await expect(helpDialog).toContainText('Farmer home')
  await helpDialog.getByRole('button', { name: 'Close contextual help' }).click()

  await page.getByRole('button', { name: 'Open profile menu' }).click()
  await page.getByRole('menuitem', { name: 'Replay guided tour' }).click()
  await expect(page.getByTestId('guided-tour')).toBeVisible()
  await page.getByTestId('guided-tour').getByRole('button', { name: 'Skip tour' }).click()
})
