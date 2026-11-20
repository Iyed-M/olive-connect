import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { cancelHarvestRequest, requestErrorMessage, type HarvestRequest } from './harvestRequests'

export function CancelRequestButton({ request }: { request: HarvestRequest }) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const mutation = useMutation({
    mutationFn: () => cancelHarvestRequest(request.id, request.version),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['harvest-request', saved.id], saved)
      await queryClient.invalidateQueries({ queryKey: ['harvest-requests'] })
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      setConfirming(false)
    },
  })
  const error = mutation.error instanceof ApiError ? mutation.error : mutation.error

  if (confirming) {
    return (
      <div className="flex flex-col gap-2 rounded-lg border border-destructive/40 bg-destructive/10 p-3">
        <p className="text-sm">Cancel this harvest request?</p>
        <div className="flex flex-wrap gap-2">
          <Button variant="destructive" size="sm" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            {mutation.isPending ? 'Cancelling...' : 'Yes, cancel'}
          </Button>
          <Button variant="outline" size="sm" disabled={mutation.isPending} onClick={() => setConfirming(false)}>Keep request</Button>
        </div>
        {error && <p role="alert" className="text-sm text-destructive">{requestErrorMessage(error)}</p>}
      </div>
    )
  }

  return <Button variant="destructive" size="sm" onClick={() => setConfirming(true)}>Cancel request</Button>
}
