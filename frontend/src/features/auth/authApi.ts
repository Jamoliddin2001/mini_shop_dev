import { baseApi } from '@/app/api/baseApi';
import { setCredentials } from '@/features/auth/authSlice';
import { logger } from '@/shared/lib/logger';
import type {
  AuthResponse,
  LoginRequest,
  MeResponse,
  RegisterRequest,
} from '@/shared/types/api';

/**
 * Auth endpoints injected into the shared baseApi. Keeping them in the feature folder
 * (not in baseApi) is the RTK Query convention for code-splitting endpoints by feature.
 */
export const authApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    login: builder.mutation<AuthResponse, LoginRequest>({
      query: (body) => ({ url: '/auth/login', method: 'POST', body }),
      // On success, persist the session through the single setCredentials path
      // (updates store + localStorage). Errors propagate to the form unchanged.
      async onQueryStarted(_arg, { dispatch, queryFulfilled }) {
        try {
          const { data } = await queryFulfilled;
          dispatch(
            setCredentials({ token: data.accessToken, email: data.email, role: data.role }),
          );
        } catch {
          logger.warn('[authApi] login failed');
        }
      },
    }),

    // Backend returns 201 with no body.
    register: builder.mutation<void, RegisterRequest>({
      query: (body) => ({ url: '/auth/register', method: 'POST', body }),
    }),

    getMe: builder.query<MeResponse, void>({
      query: () => ({ url: '/me' }),
      providesTags: ['Me'],
    }),
  }),
});

export const { useLoginMutation, useRegisterMutation, useGetMeQuery, useLazyGetMeQuery } = authApi;
