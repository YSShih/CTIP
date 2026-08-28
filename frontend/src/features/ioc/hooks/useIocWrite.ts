import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../api/client';
import {
  fetchImportJob,
  importIocs,
  reportFalsePositive,
  submitIoc,
  type FalsePositiveRequest,
  type ImportFormat,
  type ImportJobDto,
  type IocSubmitRequest,
} from '../api/iocWriteApi';
import type { IocDto } from '../types';

/** 匯入 job 的終態(§9.7):到終態就停止輪詢。 */
const TERMINAL_STATUSES = ['SUCCESS', 'PARTIAL', 'FAILURE'];

export function isTerminal(job: ImportJobDto | undefined): boolean {
  return job !== undefined && TERMINAL_STATUSES.includes(job.status ?? '');
}

export function useSubmitIoc() {
  const queryClient = useQueryClient();
  return useMutation<IocDto, ApiError, IocSubmitRequest>({
    mutationFn: submitIoc,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ioc'] }),
  });
}

export function useImportIocs() {
  return useMutation<ImportJobDto, ApiError, { format: ImportFormat; payload: string }>({
    mutationFn: ({ format, payload }) => importIocs(format, payload),
  });
}

/**
 * 匯入進度(§9.7:202 之後以 jobId 查詢)。
 * 到終態即停止輪詢——否則一個已完成的 job 會每兩秒打一次後端直到使用者離開頁面。
 */
export function useImportJob(jobId: string | null) {
  return useQuery<ImportJobDto, ApiError>({
    queryKey: ['ioc', 'import', jobId],
    queryFn: () => fetchImportJob(jobId as string),
    enabled: jobId !== null,
    refetchInterval: (query) => (isTerminal(query.state.data) ? false : 2000),
  });
}

export function useReportFalsePositive(id: string) {
  const queryClient = useQueryClient();
  return useMutation<IocDto, ApiError, FalsePositiveRequest>({
    mutationFn: (body) => reportFalsePositive(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ioc', 'detail', id] }),
  });
}
