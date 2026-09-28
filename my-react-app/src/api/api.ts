import { API_BASE_URL } from "../config";

type ApiOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
};

export class ApiError extends Error {
  status: number;
  data: unknown;

  constructor(status: number, message: string, data?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.data = data;
  }
}

async function request<T>(
  endpoint: string,
  options: ApiOptions = {}
): Promise<T> {
  const { body, headers, ...rest } = options;

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...rest,

    headers: {
      Accept: "application/json",

      ...(body !== undefined
        ? { "Content-Type": "application/json" }
        : {}),

      ...headers,
    },

    body: body !== undefined
      ? JSON.stringify(body)
      : undefined,
  });

  if (!response.ok) {
    let errorData: unknown;

    try {
      errorData = await response.json();
    } catch {
      errorData = await response.text();
    }

    throw new ApiError(
      response.status,
      `API request failed: ${response.status} ${response.statusText}`,
      errorData
    );
  }

  // Handles endpoints that intentionally return no body.
  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const api = {
  get<T>(endpoint: string): Promise<T> {
    return request<T>(endpoint, {
      method: "GET",
    });
  },

  post<T>(endpoint: string, body?: unknown): Promise<T> {
    return request<T>(endpoint, {
      method: "POST",
      body,
    });
  },

  put<T>(endpoint: string, body?: unknown): Promise<T> {
    return request<T>(endpoint, {
      method: "PUT",
      body,
    });
  },

  patch<T>(endpoint: string, body?: unknown): Promise<T> {
    return request<T>(endpoint, {
      method: "PATCH",
      body,
    });
  },

  delete<T>(endpoint: string): Promise<T> {
    return request<T>(endpoint, {
      method: "DELETE",
    });
  },
};