export interface LogoutActions {
  requestLogout: () => Promise<unknown>;
  clearLocalState: () => void;
  navigateToLogin: () => Promise<unknown>;
  notifyFailure: () => void;
}

export async function performLogout(actions: LogoutActions): Promise<boolean> {
  try {
    await actions.requestLogout();
  } catch {
    actions.notifyFailure();
    return false;
  }

  actions.clearLocalState();
  await actions.navigateToLogin();
  return true;
}
