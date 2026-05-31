import { useDispatch, useSelector } from 'react-redux';
import type { AppDispatch, RootState } from '@/app/store';

// Typed versions of the Redux hooks — use these throughout the app instead of the
// plain react-redux hooks so state and dispatch are fully typed.
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
