import { test, expect } from '@playwright/test';
import { loginAsAdmin } from './helpers';

test.describe('AI Assistant', () => {
  test('sends a question and receives a grounded reply from the LLM', async ({ page }) => {
    // Real Ollama completion, not mocked - small local models can take a while on CPU.
    test.setTimeout(60_000);

    await loginAsAdmin(page);
    await page.goto('/ai-assistant');

    await page.locator('.composer input').fill('What is the average quality score right now?');
    await page.locator('.composer button').click();

    await expect(page.locator('.message.from-user .message-bubble')).toHaveText(
      'What is the average quality score right now?',
    );

    // Wait specifically for a *settled* assistant bubble (not the transient "thinking"
    // placeholder, which also renders as a plain, non-"from-user" `.message` and would
    // make a looser count/class check pass even when the request actually failed and no
    // real assistant message was ever appended).
    const assistantBubble = page.locator('.message:not(.from-user) .message-bubble:not(.thinking)');
    await expect(assistantBubble).toBeVisible({ timeout: 45_000 });
    await expect(assistantBubble).not.toBeEmpty();
  });
});
