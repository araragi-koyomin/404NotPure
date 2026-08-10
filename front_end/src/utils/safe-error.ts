export interface SafeRequestError {
  name: string;
  code?: string;
  status?: number;
}

function recordOf(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null
    ? value as Record<string, unknown>
    : undefined;
}

export function describeRequestError(error: unknown): SafeRequestError {
  const value = recordOf(error);
  const response = recordOf(value?.response);
  const name = typeof value?.name === 'string' ? value.name : 'RequestError';
  const code = typeof value?.code === 'string' ? value.code : undefined;
  const status = typeof response?.status === 'number' ? response.status : undefined;

  return {
    name,
    ...(code === undefined ? {} : { code }),
    ...(status === undefined ? {} : { status }),
  };
}
