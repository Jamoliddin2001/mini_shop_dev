/**
 * Backend API contract types — mirror the Spring Boot DTOs exactly.
 * Source of truth: `backend/src/main/java/com/shop/{auth,user}` and `docs/api.md`.
 * The backend is NOT changed to fit the frontend; these follow it.
 */

/** User authorization role (com.shop.user.domain.Role). */
export type Role = 'ADMIN' | 'USER';

/** Request body for POST /api/auth/login and the email part of register. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Request body for POST /api/auth/register (password 8..72 chars, enforced by backend). */
export interface RegisterRequest {
  email: string;
  password: string;
}

/** Response of POST /api/auth/login (com.shop.auth.dto.AuthResponse). */
export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number; // seconds
  email: string;
  role: Role;
}

/** Response of GET /api/me (com.shop.user.dto.MeResponse). */
export interface MeResponse {
  email: string;
  role: Role;
}

/** A single field validation error, present in ApiError.violations on 400. */
export interface ApiViolation {
  field: string;
  message: string;
}

/** Unified error envelope returned by the backend @RestControllerAdvice. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  violations?: ApiViolation[];
}

/* ------------------------------------------------------------------ *
 * Phase 8 — product/cart/order contracts.
 * Mirror the backend DTO records in com.shop.{product,cart,order}.
 * NOTE on money: backend fields are BigDecimal; Jackson serializes them as JSON
 * numbers (e.g. 89.50 -> 89.5). We type them as `number` and only ever format for
 * display via Intl.NumberFormat (shared/lib/format.ts) — the frontend never does
 * money arithmetic; totals come from the server.
 * ------------------------------------------------------------------ */

/** Generic pagination envelope (com.shop.common.web.PageResponse<T>). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Order lifecycle status (com.shop.order.domain.OrderStatus). */
export type OrderStatus = 'NEW' | 'PAID' | 'CANCELLED';

/** A catalog product (com.shop.product.dto.ProductResponse). */
export interface Product {
  id: number;
  name: string;
  description: string | null;
  price: number;
  imageUrl: string | null;
  categoryId: number | null;
  categoryName: string | null;
  createdAt: string;
}

/** A product category (com.shop.product.dto.CategoryResponse). */
export interface Category {
  id: number;
  name: string;
}

/**
 * Body for POST/PUT /api/products (ProductCreateRequest / ProductUpdateRequest — PUT
 * is a full replacement). Validation mirrors the backend: name required (<=255),
 * price required (>=0, <=10 integer + 2 fraction digits), imageUrl a URL (<=1024),
 * description <=4000, categoryId positive and must reference an existing category.
 */
export interface ProductWriteRequest {
  name: string;
  description?: string;
  price: number;
  imageUrl?: string;
  categoryId?: number;
}

/** Query params for GET /api/products. Empty fields are dropped before the request. */
export interface ProductFilter {
  name?: string;
  categoryId?: number;
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
  /** `field,direction`; whitelist: name | price | createdAt. Default createdAt,desc. */
  sort?: string;
}

/** A single line in the server-side cart (com.shop.cart.dto.CartItemResponse). */
export interface CartItem {
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

/** The user's cart (com.shop.cart.dto.CartResponse). */
export interface Cart {
  items: CartItem[];
  totalAmount: number;
  totalItems: number;
}

/** Body for POST /api/cart/items (com.shop.cart.dto.AddToCartRequest). */
export interface AddToCartRequest {
  productId: number;
  quantity: number;
}

/** A single line in a placed order (com.shop.order.dto.OrderItemResponse). */
export interface OrderItem {
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

/** Full order with lines (com.shop.order.dto.OrderResponse). */
export interface Order {
  id: number;
  status: OrderStatus;
  totalAmount: number;
  createdAt: string;
  items: OrderItem[];
}

/** Order summary without lines, for the history list (OrderSummaryResponse). */
export interface OrderSummary {
  id: number;
  status: OrderStatus;
  totalAmount: number;
  createdAt: string;
}
